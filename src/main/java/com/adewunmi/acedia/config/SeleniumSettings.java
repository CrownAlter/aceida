package com.adewunmi.acedia.config;

import lombok.Data;

@Data
public class SeleniumSettings {
    private int webDriverTimeout = 30000; // 30 seconds default
    private int pageLoadTimeout = 45000; // 45 seconds for page load
    private int scriptTimeout = 20000; // 20 seconds for JS execution
}
