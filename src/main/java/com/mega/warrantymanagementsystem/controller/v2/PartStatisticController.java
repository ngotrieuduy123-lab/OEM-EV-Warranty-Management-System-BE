package com.mega.warrantymanagementsystem.controller.v2;

import com.mega.warrantymanagementsystem.model.response.PartStatisticResponse;
import com.mega.warrantymanagementsystem.service.v2.PartStatisticService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/statistic/parts")
@CrossOrigin
@SecurityRequirement(name = "api")
public class PartStatisticController {

    @Autowired
    private PartStatisticService service;

    @GetMapping("/all")
    public List<PartStatisticResponse> getAll() {
        return service.getAllStatistic();
    }

    @GetMapping("/date")
    public List<PartStatisticResponse> getByDate(@RequestParam String date) {
        return service.getStatisticByDate(flexParse(date));
    }

    @GetMapping("/month")
    public List<PartStatisticResponse> getByMonth(@RequestParam int year, @RequestParam int month) {
        return service.getStatisticByMonth(year, month);
    }

    @GetMapping("/year")
    public List<PartStatisticResponse> getByYear(@RequestParam int year) {
        return service.getStatisticByYear(year);
    }

    private LocalDate flexParse(String txt) {
        List<String> fmts = List.of("dd/MM/yyyy", "yyyy-MM-dd");
        for (String f : fmts) {
            try {
                return LocalDate.parse(txt, DateTimeFormatter.ofPattern(f));
            } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("Ngày không hợp lệ, dùng dd/MM/yyyy hoặc yyyy-MM-dd");
    }

}
