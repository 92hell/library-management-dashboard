package com.helmifr.library.entity.form;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class AuthorSearchForm {
    private String name;
    private Date dateOfBirth;
}
