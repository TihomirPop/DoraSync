package hr.tvz.popovic.dorasync.application.domain.model;

import static java.util.Objects.requireNonNull;

public record ImageVersion(String value) {

    public ImageVersion {
        requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    /**
     * The build number encoded in an image version of the form {@code {buildNumber}-{sha.first7}}.
     * Returns {@link Maybe.None} when the leading segment is not a non-negative number, so an
     * unparseable version never throws.
     */
    public Maybe<BuildNumber> buildNumber() {
        int dash = value.indexOf('-');
        String candidate = dash < 0 ? value : value.substring(0, dash);
        try {
            // Long.parseLong throws NumberFormatException; BuildNumber rejects negatives with
            // IllegalArgumentException — the supertype covers both.
            return Maybe.of(new BuildNumber(Long.parseLong(candidate)));
        } catch (IllegalArgumentException e) {
            return new Maybe.None<>();
        }
    }
}
