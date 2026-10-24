package com.sentinel.modules.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParameterDto(
        String name,
        String in,
        boolean required,
        String type,
        String description,
        String example
) {
}
