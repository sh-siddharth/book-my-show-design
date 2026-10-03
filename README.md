# BookMyShow Production LLD: Code Blueprint & Class Architecture

## 1. Executive Summary & Core Architectural Highlights
To solve the concurrent seat booking problem at scale without double-booking:
1. **Tier 1 - Redis Volatile Lock:** Atomic Lua Script (`SETNX` with TTL) prevents database overload during high-concurrency ticket drops.
2. **Tier 2 - DB Optimistic Locking:** `@Version` annotation on the `Seat` entity ensures database-level atomicity across distributed application pods.
3. **Tier 3 - State Machine Enforcement:** Enum-based state pattern guarantees strict transitions (`AVAILABLE` -> `LOCKED` -> `BOOKED`).
4. **Tier 4 - Async Expiry & DLQ:** Delayed queue triggers hold release on payment timeout.

---

## 2. Textual Class & Relationship Diagram

```
+--------------------------------------------------------------------------------+
|                                    CLIENT                                      |
+--------------------------------------------------------------------------------+
                                       |
                                       v
+--------------------------------------------------------------------------------+
|                             BookingService                                     |
|  - lockManager: DistributedLockManager                                         |
|  - seatRepository: SeatRepository                                              |
|  + reserveSeats(showId, seatIds, userId): BookingResponse                      |
+--------------------------------------------------------------------------------+
             |                                              |
             | (uses Strategy)                              | (persists)
             v                                              v
+-----------------------------+               +----------------------------------+
| <<interface>>               |               |            Seat                  |
| DistributedLockManager      |               |  - seatId: String                |
| + acquireLock(key, val, ttl)|               |  - status: SeatStatus            |
| + releaseLock(key, val)     |               |  - version: Long                 |
+-----------------------------+               |  - lockedBy: String              |
              ^                               |  + transitionTo(newStatus): void |
              | (implements)                  +----------------------------------+
+-----------------------------+                                |
|   RedisLockManager          |                                | (uses State)
| - redisTemplate: Redis      |                                v
+-----------------------------+               +----------------------------------+
                                              |        <<enum / State>>          |
                                              |           SeatStatus             |
                                              |  - AVAILABLE                     |
                                              |  - LOCKED                        |
                                              |  - BOOKED                        |
                                              |  + canTransitionTo(next): bool   |
                                              +----------------------------------+
```

---

## 3. High-Level Java Code Blueprint (Interfaces, State Machine, Core Orchestration)

### Seat Status Enum State Machine
```java
public enum SeatStatus {
    AVAILABLE {
        @Override
        public boolean canTransitionTo(SeatStatus next) {
            return next == LOCKED;
        }
    },
    LOCKED {
        @Override
        public boolean canTransitionTo(SeatStatus next) {
            return next == BOOKED || next == AVAILABLE; // AVAILABLE on payment failure/timeout
        }
    },
    BOOKED {
        @Override
        public boolean canTransitionTo(SeatStatus next) {
            return false; // Terminal state
        }
    };

    public abstract boolean canTransitionTo(SeatStatus next);
}
```

### Domain Entity with Optimistic Locking
```java
public class Seat {
    private String seatId;
    private String showId;
    private SeatStatus status;
    private String lockedBy;
    private Long version; // Optimistic locking tag

    public synchronized void transitionTo(SeatStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException("Invalid state transition from " + this.status + " to " + newStatus);
        }
        this.status = newStatus;
    }

    // Getters and Setters
}
```

### Pluggable Distributed Lock Strategy Interface & Redis Concrete Class
```java
public interface DistributedLockManager {
    boolean acquireLock(String key, String value, long ttlInSeconds);
    boolean releaseLock(String key, String value);
}

public class RedisLockManager implements DistributedLockManager {
    private final StringRedisTemplate redisTemplate;

    public RedisLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquireLock(String key, String value, long ttlInSeconds) {
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlInSeconds));
        return Boolean.TRUE.equals(success);
    }

    @Override
    public boolean releaseLock(String key, String value) {
        // Atomic release via Lua Script
        String luaScript = 
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "   return redis.call('del', KEYS[1]) " +
            "else " +
            "   return 0 " +
            "end";
        return Boolean.TRUE.equals(
            redisTemplate.execute(new DefaultRedisScript<>(luaScript, Boolean.class),
                    Collections.singletonList(key), value)
        );
    }
}
```

### Core Orchestration Service
```java
public class BookingService {
    private final DistributedLockManager lockManager;
    private final SeatRepository seatRepository;

    public BookingService(DistributedLockManager lockManager, SeatRepository seatRepository) {
        this.lockManager = lockManager;
        this.seatRepository = seatRepository;
    }

    public boolean reserveSeat(String showId, String seatId, String userId) {
        String lockKey = "lock:show:" + showId + ":seat:" + seatId;
        String requestId = UUID.randomUUID().toString();

        // Step 1: Distributed Lock via Redis (TTL 600s = 10 mins hold)
        boolean lockAcquired = lockManager.acquireLock(lockKey, requestId, 600);
        if (!lockAcquired) {
            throw new SeatAlreadyLockedException("Seat currently held by another user.");
        }

        try {
            // Step 2: Fetch Entity & Validate State Machine
            Seat seat = seatRepository.findByShowAndSeat(showId, seatId);
            seat.transitionTo(SeatStatus.LOCKED);
            seat.setLockedBy(userId);

            // Step 3: DB Persistence with Optimistic Lock (@Version check)
            seatRepository.saveWithOptimisticLock(seat);
            return true;

        } catch (Exception ex) {
            // Rollback volatile lock on failure
            lockManager.releaseLock(lockKey, requestId);
            throw ex;
        }
    }
}
```
