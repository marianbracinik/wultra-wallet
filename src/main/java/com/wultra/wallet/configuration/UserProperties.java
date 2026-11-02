package com.wultra.wallet.configuration;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties("wallet.users")
public class UserProperties {

    @NotBlank
    private String alicePassword;

    @NotBlank
    private String bobPassword;

    @Override
    public String toString() { return "UserProperties[--hidden--]"; }

}
