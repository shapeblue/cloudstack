package org.apache.cloudstack.framework.config.impl;

import com.cloud.utils.exception.CloudRuntimeException;

public class DiagnosticsKey<T> {
    public static enum DiagnosticsEntryType {
        IPTABLES, LOGFILES, PROPERTYFILES, DHCPFILES, USERDATA, LB, DNS, VPN, IPTABLESretrieve, IPTABLESremove
    }

    private String _role;
    private DiagnosticsEntryType _diagnosticsType;
    private String _defaultFiles;

    public String role() {
        return _role;
    }

    public final String key()
    {
        return _role;
    }

    public String _diagnosticsType()
    {
        return _diagnosticsType.name();
    }

    public String defaultFiles() {
        return _defaultFiles;
    }

    public DiagnosticsEntryType diagnosticsType()
    {
        return _diagnosticsType;
    }



/*    public ConfigKey.Scope scope() {
        return _scope;
    }
*/
//    public DiagnosticsType diagnosticsType() { return _diagnosticsType; }

    @Override
    public String toString()
    {
        return _role;
    }

    private final Class<T> _type;
    private final String _role;


    private final String _defaultValue;
    private final String _description;
    private final boolean _isDynamic;
//    private final T _multiplier;
    T _value = null;

//    private final T _multiplier;
 //   private final String _defaultvalue = null;

    static DiagnosticsConfigDepotImpl s_depot = null;

    static public void init(DiagnosticsConfigDepotImpl depot) {
        s_depot = depot;
    }

    public DiagnosticsKey(String category, Class<T> type, String name, String defaultValue, String description, boolean isDynamic) {
        _category = category;
        _type = type;
        _role = name;
        _defaultValue = defaultValue;
        _description = description;
 //       _scope = scope;
        _isDynamic = isDynamic;
    }

    @Override
    public int hashCode()
    {
        return _role.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DiagnosticsKey) {
            DiagnosticsKey<?> that = (DiagnosticsKey<?>)obj;
            return this._role.equals(that._role);
        }
        return false;
    }

    public boolean isSameKeyAs(Object obj) {
        if(this.equals(obj)) {
            return true;
        } else if (obj instanceof String) {
            String key = (String)obj;
            return key.equals(_role);
        }

        throw new CloudRuntimeException("Comparing ConfigKey to " + obj.toString());
    }

    public T value() {
        if (_value == null) {
            RetrieveDiagnosticsVO vo = s_depot != null ? s_depot.global().findById(key()) : null;
            final String value = (vo != null && vo.getValue() != null) ? vo.getValue() : null;
            _value = ((value == null) ? (T)null : valueOf(value));
        }

        return _value;
    }

    public T valueIn(Long id) {
        if (id == null) {
            return value();
        }

        String value = s_depot != null ? s_depot.findScopedConfigStorage(this).getConfigValue(id, this) : null;
        if (value == null) {
            return value();
        } else {
            return valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    protected T valueOf(String value) {
        Class<T> type = type();
        if (!type.isAssignableFrom(String.class)) {
            throw new CloudRuntimeException("Unsupported data type for config values: " + type);
        } else {
            return (T) String.valueOf(value);
        }
    }

}
