package dev.blockfolk.model;

public record AiMemory(String fact, Category category, StoredLocation origin, long recordedAt) {
    public static final long TEMPORAL_LIFETIME_MILLIS = 24L * 60 * 60 * 1000;

    public enum Category {
        PERSONAL, REGIONAL, TEMPORAL
    }

    public AiMemory(String fact, Category category) {
        this(fact, category, null, System.currentTimeMillis());
    }

    public AiMemory {
        category = category == null ? Category.PERSONAL : category;
    }

    public boolean expired(long now) {
        return category == Category.TEMPORAL && recordedAt > 0 && now - recordedAt >= TEMPORAL_LIFETIME_MILLIS;
    }

    public boolean reaches(StoredLocation location) {
        if (category != Category.REGIONAL || origin == null || location == null
                || !origin.worldName().equals(location.worldName()))
            return false;
        double dx = origin.x() - location.x();
        double dy = origin.y() - location.y();
        double dz = origin.z() - location.z();
        return dx * dx + dy * dy + dz * dz <= 50 * 50;
    }
}
