package {{ package }};

/**
 * Tags storage class, you can change at will
 */
public class Reference {
    private Reference() {}

    public static final String MOD_ID = "{{ mod_id }}";
    public static final String MOD_NAME = "{{ mod_name }}";
    public static final String VERSION = "{{ mod_version }}";
    public static final String CLIENT_PROXY_CLASS = "{{ client_proxy_class }}";
    public static final String SERVER_PROXY_CLASS = "{{ server_proxy_class }}";
}
