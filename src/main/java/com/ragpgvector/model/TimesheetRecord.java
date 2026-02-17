package com.ragpgvector.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a timesheet record from the relational database
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimesheetRecord {

    private Long id;
    private String monthYear;
    private String assignmentName;
    private String typecode;
    private Integer hours;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Helper method to extract year from monthYear
    public String getYear() {
        if (monthYear == null) return null;
        String[] parts = monthYear.split(" ");
        return parts.length > 1 ? parts[1] : null;
    }

    // Helper method to extract month from monthYear
    public String getMonth() {
        if (monthYear == null) return null;
        String[] parts = monthYear.split(" ");
        return parts.length > 0 ? parts[0] : null;
    }

    // Helper method to check if record matches a specific year
    public boolean isInYear(String year) {
        return year != null && year.equals(getYear());
    }

    // Helper method to check if record matches a specific month
    public boolean isInMonth(String month) {
        return month != null && month.equalsIgnoreCase(getMonth());
    }

    // Helper method to check if record matches a specific project
    public boolean isForProject(String projectName) {
        return projectName != null && assignmentName != null &&
               assignmentName.toLowerCase().contains(projectName.toLowerCase());
    }
}
