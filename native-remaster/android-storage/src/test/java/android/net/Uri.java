package android.net;

import java.util.Objects;

public final class Uri {
    private final String value;

    private Uri(String value) {
        this.value = value;
    }

    public static Uri parse(String value) {
        return new Uri(Objects.requireNonNull(value, "value"));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Uri && value.equals(((Uri) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
