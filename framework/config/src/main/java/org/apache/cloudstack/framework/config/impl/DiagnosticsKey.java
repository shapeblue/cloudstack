package org.apache.cloudstack.framework.config.impl;

import com.cloud.utils.exception.CloudRuntimeException;

public class DiagnosticsKey {
    public static enum DiagnosticsEntryType {
        IPTABLES, LOGFILES, PROPERTYFILES, DHCPFILES, USERDATA, LB, DNS, VPN, IPTABLESretrieve, IPTABLESremove
    }

    private String _role;
    private DiagnosticsEntryType _diagnosticsType;
    private String _defaultFiles;
    private String _description;

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

    public String diagnosticsType()
    {
        return _diagnosticsType.name();
    }

    public String description() {
        return _description;
    }



    @Override
    public String toString()
    {
        return _diagnosticsType.name();
    }

    static DiagnosticsConfigDepotImpl s_depot = null;

    static public void init(DiagnosticsConfigDepotImpl depot) {
        s_depot = depot;
    }

    public DiagnosticsKey(String role, DiagnosticsEntryType diagnosticstype, String description) {
        _role = role;
        _diagnosticsType = diagnosticstype;
        _description = description;
    }

    @Override
    public int hashCode()
    {
        return _role.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DiagnosticsKey) {
            DiagnosticsKey that = (DiagnosticsKey)obj;
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

    public String value() {
        if (_defaultFiles == null) {
            RetrieveDiagnosticsVO vo = s_depot != null ? s_depot.global().findById(key()) : null;
            final String value = (vo != null && vo.getValue() != null) ? vo.getValue() : null;
            _defaultFiles = (String)((value == null) ? null : valueOf(value));
        }

        return _defaultFiles;
    }

    public String valueIn(Long id) {
        if (id == null) {
            return defaultFiles();
        }

        String value = s_depot != null ? s_depot.findScopedConfigStorage(this).getConfigValue(id, this) : null;
        if (value == null) {
            return value();
        } else {
            return valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    protected String valueOf(String value) {
        Class type = value.getClass();
        if (!type.isAssignableFrom(String.class)) {
            throw new CloudRuntimeException("Unsupported data type for config values: " + type);
        } else {
            return  String.valueOf(value);
        }
    }

}
