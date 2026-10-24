package com.sentinel.modules.scanner.rule;

import com.sentinel.modules.project.entity.ApiEndpoint;
import com.sentinel.modules.scanner.entity.SecurityFinding;
import com.sentinel.modules.scanner.entity.SecurityScan;

import java.util.List;

/**
 * Service interface for modular security checks.
 * New security checks can be implemented independently and will be auto-discovered by the scanner.
 */
public interface ScannerCheck {

    String getRuleId();

    String getRuleName();

    String getOwaspCategory();

    List<SecurityFinding> execute(ApiEndpoint endpoint, ScanContext context, SecurityScan scan);
}
