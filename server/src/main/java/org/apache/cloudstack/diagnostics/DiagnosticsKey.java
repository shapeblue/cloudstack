package org.apache.cloudstack.diagnostics;

import com.cloud.utils.exception.CloudRuntimeException;
import org.apache.cloudstack.diagnostics.impl.DiagnosticsConfigDepotImpl;
import org.apache.cloudstack.diagnostics.impl.RetrieveDiagnosticsVO;

import java.sql.Date;

public class DiagnosticsKey<T> {
    public static enum DiagnosticsType {
        LOGFILES, PROPERTYFILES, CONFIGURATIONFILES, DHCPFILES, USERDATA, LB, DNS, VPN, IPTABLESretrieve, IPTABLESremove
    }

    public Class<T> type() {
        return _type;
    }

    public final String key()
    {
        return _role;
    }

    public String _diagnosticsType()
    {
        return _diagnosticsType.name();
    }

    public String role()
    {
        return _role;
    }

    public DiagnosticsType diagnosticsType() { return _diagnosticsType; }

    @Override
    public String toString()
    {
        return _role;
    }

    private final Class<T> _type;
    private final String _role;
    private final DiagnosticsType _diagnosticsType;
    private final T _multiplier;
    T _value = null;

    static DiagnosticsConfigDepotImpl s_depot = null;

    static public void init(DiagnosticsConfigDepotImpl depot) {
        s_depot = depot;
    }

    public DiagnosticsKey(Class<T> type, String name, DiagnosticsType className, String defaultValue, String role, T multiplier)  {
        _type = type;
        _role = role;
        _diagnosticsType = className;
        _multiplier = multiplier;
    }

    public T multiplier() {
        return _multiplier;
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
            final String value = (vo != null && vo.getValue() != null) ? vo.getValue() : diagnosticsType().name();
            _value = ((value == null) ? (T)diagnosticsType() : valueOf(value));
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
        Number multiplier = 1;
        if (multiplier() != null) {
            multiplier = (Number)multiplier();
        }
        Class<T> type = type();
        if (type.isAssignableFrom(Boolean.class)) {
            return (T)Boolean.valueOf(value);
        } else if (type.isAssignableFrom(Integer.class)) {
            return (T)new Integer(Integer.parseInt(value) * multiplier.intValue());
        } else if (type.isAssignableFrom(Long.class)) {
            return (T)new Long(Long.parseLong(value) * multiplier.longValue());
        } else if (type.isAssignableFrom(Short.class)) {
            return (T)new Short(Short.parseShort(value));
        } else if (type.isAssignableFrom(String.class)) {
            return (T)value;
        } else if (type.isAssignableFrom(Float.class)) {
            return (T)new Float(Float.parseFloat(value) * multiplier.floatValue());
        } else if (type.isAssignableFrom(Double.class)) {
            return (T)new Double(Double.parseDouble(value) * multiplier.doubleValue());
        } else if (type.isAssignableFrom(String.class)) {
            return (T)value;
        } else if (type.isAssignableFrom(Date.class)) {
            return (T)Date.valueOf(value);
        } else if (type.isAssignableFrom(Character.class)) {
            return (T)new Character(value.charAt(0));
        } else {
            throw new CloudRuntimeException("Unsupported data type for config values: " + type);
        }
    }



}
