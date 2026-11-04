package io.kaoto.forage.maven.catalog;

/**
 * A {@code @ForageBean} class that is deliberately not attached to any {@code @ForageFactory}, and therefore not in
 * the catalog. Configured as an {@code <excludedBean>} entry of the plugin's {@code <excludedBeans>} list:
 *
 * <pre>{@code
 * <excludedBeans>
 *     <excludedBean>
 *         <className>io.kaoto.forage.example.ExampleProvider</className>
 *         <reason>Pending #123: no factory declares camel-example yet</reason>
 *     </excludedBean>
 * </excludedBeans>
 * }</pre>
 *
 * The reason is mandatory, so that every exclusion can be reviewed and removed once its factory exists.
 */
public class ExcludedBean {

    private String className;
    private String reason;

    public ExcludedBean() {}

    public ExcludedBean(String className, String reason) {
        this.className = className;
        this.reason = reason;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
