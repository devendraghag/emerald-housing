package com.emerald.member.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.regex.Pattern;

public class MobileNumberValidator implements ConstraintValidator<MobileNumber, String> {

    private final Pattern mobileNumberPattern;

    public MobileNumberValidator(
            @Value("${app.member.mobile-number-regex:^[6-9][0-9]{9}$}") String mobileNumberRegex) {
        this.mobileNumberPattern = Pattern.compile(mobileNumberRegex);
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && mobileNumberPattern.matcher(value.trim()).matches();
    }
}
