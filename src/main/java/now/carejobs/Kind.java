package now.carejobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A kind of healthcare job, the {@code kind} filter of the API.
 *
 * <p>Anywhere a kind is accepted you may also pass its slug as a string (for
 * example {@code "nurses"}), so a kind the server adds later still works.
 */
public enum Kind {
    /** Registered nurses and LPNs. */
    NURSES("nurses"),
    /** Nurse practitioners and physician assistants. */
    NURSE_PRACTITIONERS("nurse-practitioners"),
    /** Doctors. */
    PHYSICIANS("physicians"),
    /** CNAs and patient care techs. */
    NURSING_ASSISTANTS("nursing-assistants"),
    /** Caregivers and home health aides. */
    CAREGIVERS("caregivers"),
    /** Physical, occupational, speech and respiratory therapy. */
    THERAPY_REHAB("therapy-rehab"),
    /** Mental health and social work. */
    MENTAL_HEALTH("mental-health"),
    /** Pharmacists and pharmacy techs. */
    PHARMACY("pharmacy"),
    /** Radiology and imaging techs. */
    IMAGING("imaging"),
    /** Lab. */
    LAB("lab"),
    /** Surgical techs. */
    SURGICAL_TECHS("surgical-techs"),
    /** EMTs and paramedics. */
    EMTS_PARAMEDICS("emts-paramedics"),
    /** Medical assistants. */
    MEDICAL_ASSISTANTS("medical-assistants"),
    /** Dental. */
    DENTAL("dental"),
    /** Front desk and admin. */
    ADMIN("admin"),
    /** Allied health. */
    ALLIED_HEALTH("allied-health"),
    /** AI training work for health professionals. */
    AI_TRAINING("ai-training");

    private final String value;

    Kind(String value) {
        this.value = value;
    }

    /**
     * The slug the API uses, for example {@code "nurses"}.
     *
     * @return the slug
     */
    public String value() {
        return value;
    }

    /**
     * The slug, the same as {@link #value()}.
     *
     * @return the slug
     */
    @Override
    public String toString() {
        return value;
    }

    /**
     * The kind with this slug.
     *
     * @param value a slug such as {@code "nursing-assistants"}
     * @return the kind
     * @throws IllegalArgumentException when no kind has this slug
     */
    public static Kind fromValue(String value) {
        for (Kind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("No kind of job is called \"" + value + "\"");
    }

    /**
     * Every slug, in the order the API documents them.
     *
     * @return the 17 slugs, read-only
     */
    public static List<String> slugs() {
        List<String> slugs = new ArrayList<>();
        for (Kind kind : values()) {
            slugs.add(kind.value);
        }
        return Collections.unmodifiableList(slugs);
    }
}
