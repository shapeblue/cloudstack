package org.apache.cloudstack.storage.configdrive.org.apache.cloudstack.storage.diagnostics;

public class Diagnostics {
    public final static String DIAGNOSTICSDIR = "diagnosticsdata";

    public static final String cloudStackConfigDriveName = "/cloudstack/";
    public static String createDiagnosticsPath(String instanceName) {
        return Diagnostics.DIAGNOSTICSDIR + "/" + diagnosticsFileName(instanceName);
    }

    public static String diagnosticsFileName(String instanceName) {
        return instanceName + ".zip";
    }

}
