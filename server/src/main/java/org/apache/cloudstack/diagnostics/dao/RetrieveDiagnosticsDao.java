package org.apache.cloudstack.diagnostics.dao;

import com.cloud.utils.db.GenericDao;
import org.apache.cloudstack.diagnostics.impl.RetrieveDiagnosticsVO;

import java.util.Map;

public interface RetrieveDiagnosticsDao extends GenericDao<RetrieveDiagnosticsVO, String> {

    Map<String, String> getDiagnosticsDetails();

    boolean update(String name, String category, String value);

    String getValue(String name);

    RetrieveDiagnosticsVO findByName(String name);

    String getValueAndInitIfNotExist(String name, String className, String initValue);

    void invalidateCache();

}
