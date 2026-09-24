package com.qjj.auth.common.page;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PageQuery {
    @Min(value = 1, message = "页码从 1 起")
    private long current = 1L;
    @Min(value = 1, message = "每页条数至少 1")
    @Max(value = 500, message = "每页条数最多 500")
    private long size = 10L;
}
