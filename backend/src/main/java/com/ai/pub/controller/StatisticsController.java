package com.ai.pub.controller;

import com.ai.pub.common.Result;
import com.ai.pub.dto.StatisticsDTO;
import com.ai.pub.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping
    public Result<StatisticsDTO> getStatistics(@RequestParam(required = false) Long accountId) {
        return Result.success(statisticsService.getStatistics(accountId != null ? accountId : 1L));
    }

    @GetMapping("/{accountId}")
    public Result<StatisticsDTO> getStatisticsById(@PathVariable Long accountId) {
        return Result.success(statisticsService.getStatistics(accountId));
    }
}
