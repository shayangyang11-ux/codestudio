package com.codemind.platform.theme;

public class ProjectThemeProfileRollbackResponse {

    private ProjectThemeProfileApplicationLogResponse sourceLog;
    private ProjectThemeProfileApplicationLogResponse rollbackLog;
    private ProjectThemeProfileResponse resultProfile;

    public ProjectThemeProfileRollbackResponse() {
    }

    public ProjectThemeProfileApplicationLogResponse getSourceLog() {
        return sourceLog;
    }

    public void setSourceLog(
            ProjectThemeProfileApplicationLogResponse sourceLog
    ) {
        this.sourceLog = sourceLog;
    }

    public ProjectThemeProfileApplicationLogResponse getRollbackLog() {
        return rollbackLog;
    }

    public void setRollbackLog(
            ProjectThemeProfileApplicationLogResponse rollbackLog
    ) {
        this.rollbackLog = rollbackLog;
    }

    public ProjectThemeProfileResponse getResultProfile() {
        return resultProfile;
    }

    public void setResultProfile(
            ProjectThemeProfileResponse resultProfile
    ) {
        this.resultProfile = resultProfile;
    }
}
