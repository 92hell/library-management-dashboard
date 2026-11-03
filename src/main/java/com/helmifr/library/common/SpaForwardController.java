package com.helmifr.library.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React app for client-side routes such as /books or /books/42, so reloading or
 * deep-linking works. API, actuator and static asset paths are never matched.
 */
@Controller
public class SpaForwardController {

    @GetMapping({
            "/{first:^(?!api$|actuator$|assets$)[^.]*}",
            "/{first:^(?!api$|actuator$|assets$)[^.]*}/{second:[^.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
