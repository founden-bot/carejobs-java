package now.carejobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The answer of one tool: the data as a map ({@link #data()}, or
 * {@link #get(String)}) plus {@link #text()}, the plain-text summary the server
 * writes for the answer, handy to show a person or to hand to a language model.
 *
 * <p>The keys are the ones the API sends ({@code typical_low}, {@code open_jobs},
 * and so on). Objects are {@code Map<String, Object>}, arrays are
 * {@code List<Object>}, whole numbers are {@code Long} and other numbers
 * {@code Double}. Where the API sends null for "none", the value is null. A
 * result is read-only and safe to share between threads.
 */
public final class Result {

    private final Map<String, Object> data;
    private final String text;

    /**
     * Creates a result.
     *
     * @param data the structured data
     * @param text the readable summary
     */
    public Result(Map<String, Object> data, String text) {
        this.data = Collections.unmodifiableMap(Objects.requireNonNull(data, "data"));
        this.text = Objects.requireNonNull(text, "text");
    }

    /**
     * The tool's data.
     *
     * @return the data, read-only
     */
    public Map<String, Object> data() {
        return data;
    }

    /**
     * The readable summary the server wrote.
     *
     * @return the text, empty when the server sent none
     */
    public String text() {
        return text;
    }

    /**
     * One value of the data.
     *
     * @param key a key such as {@code "total"}
     * @return the value, or null when the key is missing or its value is null
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Whether the data has this key (even with a null value).
     *
     * @param key the key
     * @return true when the key is present
     */
    public boolean has(String key) {
        return data.containsKey(key);
    }

    /**
     * A string value.
     *
     * @param key the key
     * @return the string, or null when missing or null
     * @throws ClassCastException when the value is not a string
     */
    public String getString(String key) {
        return (String) data.get(key);
    }

    /**
     * A number as a long (a fraction is cut off).
     *
     * @param key the key
     * @return the number, or null when missing or null
     * @throws ClassCastException when the value is not a number
     */
    public Long getLong(String key) {
        Number n = (Number) data.get(key);
        return n == null ? null : n.longValue();
    }

    /**
     * A number as a double.
     *
     * @param key the key
     * @return the number, or null when missing or null
     * @throws ClassCastException when the value is not a number
     */
    public Double getDouble(String key) {
        Number n = (Number) data.get(key);
        return n == null ? null : n.doubleValue();
    }

    /**
     * A boolean value.
     *
     * @param key the key
     * @return the boolean, or null when missing or null
     * @throws ClassCastException when the value is not a boolean
     */
    public Boolean getBoolean(String key) {
        return (Boolean) data.get(key);
    }

    /**
     * An object value, for example {@code getMap("pay").get("median")}.
     *
     * @param key the key
     * @return the object as a read-only map, or null when missing or null
     * @throws ClassCastException when the value is not an object
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMap(String key) {
        return (Map<String, Object>) data.get(key);
    }

    /**
     * An array value.
     *
     * @param key the key
     * @return the array as a read-only list, or null when missing or null
     * @throws ClassCastException when the value is not an array
     */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String key) {
        return (List<Object>) data.get(key);
    }

    /**
     * An array of objects, for example {@code getMaps("jobs")}.
     *
     * @param key the key
     * @return the objects (an empty list when the key is missing or null)
     * @throws ClassCastException when the value is not an array of objects
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMaps(String key) {
        List<Object> list = getList(key);
        if (list == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> maps = new ArrayList<>(list.size());
        for (Object item : list) {
            maps.add((Map<String, Object>) item);
        }
        return Collections.unmodifiableList(maps);
    }

    /**
     * The data as compact JSON.
     *
     * @return the JSON text
     */
    public String toJson() {
        return Json.write(data);
    }

    /**
     * The readable summary, the same as {@link #text()}.
     *
     * @return the text
     */
    @Override
    public String toString() {
        return text;
    }
}
