package com.helmifr.library.controller;

import com.helmifr.library.entity.Author;
import com.helmifr.library.form.AuthorSearchForm;
import com.helmifr.library.service.AuthorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@RestController
@RequestMapping("/authors")
public class AuthorController {
    @Autowired
    AuthorService authorService;

    @GetMapping
    public List<Author> getAuthors() {
        Author author = new Author();
        author.setName("");
        return authorService.searchAuthors(author);
    }

    @GetMapping("/{id}")
    public Author getAuthor(@PathVariable long id) {
        return authorService.findAuthorById(id);
    }

    @PostMapping
    public ResponseEntity<Author> createAuthor(@RequestBody Author author) throws URISyntaxException {
        Author saved = authorService.saveAuthor(author);
        return ResponseEntity.created(new URI("/clients/" + saved.getId())).body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Author> updateAuthor(@PathVariable long id, @RequestBody Author author) {
        if(authorService.findAuthorById(id) != null) {
            Author updated = authorService.saveAuthor(author);
            return ResponseEntity.ok(updated);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Author> deleteAuthor(@PathVariable long id) {
        authorService.deleteAuthorById(id);
        return ResponseEntity.ok().build();
    }
}
