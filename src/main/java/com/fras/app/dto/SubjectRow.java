package com.fras.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Just enough of a subject to choose one. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubjectRow {

    private Long id;
    private String code;
    private String name;
    private int credit;

    public SubjectRow() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCredit() {
        return credit;
    }

    public void setCredit(int credit) {
        this.credit = credit;
    }

    /** "CS201 - Data Structures", falling back to whichever half exists. */
    public String label() {
        boolean hasCode = code != null && !code.isBlank();
        boolean hasName = name != null && !name.isBlank();
        if (hasCode && hasName) {
            return code + " - " + name;
        }
        if (hasName) {
            return name;
        }
        if (hasCode) {
            return code;
        }
        return "Subject " + id;
    }
}
