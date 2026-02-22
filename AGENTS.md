# AGENTS.md

This document provides guidelines for AI coding agents working in this repository.

## Project Overview

This is a Java-based backend service for a vocabulary/word learning application. The project uses Docker for service management and PostgreSQL as the database.

## Build Commands

```bash
# Build the project
./mvnw clean install

# Build without running tests
./mvnw clean install -DskipTests

# Compile only
./mvnw compile
```

## Test Commands

```bash
# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=UserServiceTest

# Run a single test method
./mvnw test -Dtest=UserServiceTest#testCreateUser

# Run tests with specific tags
./mvnw test -Dgroups=integration

# Run tests with coverage
./mvnw test jacoco:report
```

## Lint and Code Quality

```bash
# Run checkstyle
./mvnw checkstyle:check

# Run SpotBugs
./mvnw spotbugs:check

# Run all quality checks
./mvnw verify
```

## Docker Commands

```bash
# Start all services
docker-compose up -d

# Start specific service
docker-compose up -d app db

# View logs
docker-compose logs -f app

# Stop services
docker-compose down

# Rebuild and restart
docker-compose up -d --build

# Reset database
docker-compose down -v && docker-compose up -d db
```

## Code Style Guidelines

### Imports

- Use explicit imports; avoid wildcard imports (`import java.util.*`)
- Order imports: `java.*`, `javax.*`, third-party libraries, then project packages
- Remove unused imports before committing
- Static imports should come after regular imports

```java
// Good
import java.util.List;
import java.util.Optional;
import javax.persistence.Entity;
import org.springframework.stereotype.Service;
import com.example.words.model.Word;
import com.example.words.repository.WordRepository;

// Bad
import java.util.*;
```

### Formatting

- Use 4 spaces for indentation (no tabs)
- Maximum line length: 120 characters
- Opening braces on same line
- One statement per line
- Blank line between methods
- Use blank lines to separate logical code sections

```java
public class WordService {
    
    private final WordRepository wordRepository;
    
    public WordService(WordRepository wordRepository) {
        this.wordRepository = wordRepository;
    }
    
    public Optional<Word> findById(Long id) {
        return wordRepository.findById(id);
    }
}
```

### Naming Conventions

- **Classes/Interfaces**: PascalCase (`WordService`, `WordRepository`)
- **Methods**: camelCase (`findByName`, `createWord`)
- **Variables**: camelCase (`wordList`, `userRepository`)
- **Constants**: SCREAMING_SNAKE_CASE (`MAX_RETRY_COUNT`, `DEFAULT_PAGE_SIZE`)
- **Packages**: lowercase (`com.example.words.service`)
- **Database tables**: snake_case (`word_entries`, `user_profiles`)

### Types and Null Safety

- Prefer `Optional<T>` over nullable references for return types
- Use `@NonNull` and `@Nullable` annotations where appropriate
- Prefer primitive types when possible (`long` over `Long`, `int` over `Integer`)
- Use `LocalDateTime` for timestamps, not `Date`
- Use `BigDecimal` for financial calculations

```java
// Good
public Optional<Word> findBySpelling(String spelling) {
    return wordRepository.findBySpelling(spelling);
}

// Good
public List<Word> findAll() {
    return wordRepository.findAll(); // Returns empty list, not null
}
```

### Error Handling

- Use custom exceptions for domain-specific errors
- Throw specific exceptions, not generic `Exception`
- Use `@ControllerAdvice` for global exception handling
- Log exceptions appropriately with context

```java
// Good
public Word createWord(Word word) {
    if (wordRepository.existsBySpelling(word.getSpelling())) {
        throw new DuplicateWordException(word.getSpelling());
    }
    return wordRepository.save(word);
}

// Exception handler
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(DuplicateWordException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateWord(DuplicateWordException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("WORD_EXISTS", e.getMessage()));
    }
}
```

### REST API Conventions

- Use plural nouns for resources: `/words`, `/users`
- Use HTTP methods correctly:
  - `GET` - retrieve resources
  - `POST` - create resources
  - `PUT` - replace resources
  - `PATCH` - partial update
  - `DELETE` - remove resources
- Return appropriate status codes
- Use `ResponseEntity<T>` for flexible responses

```java
@RestController
@RequestMapping("/api/words")
public class WordController {
    
    @GetMapping
    public List<Word> list() { ... }
    
    @GetMapping("/{id}")
    public ResponseEntity<Word> get(@PathVariable Long id) { ... }
    
    @PostMapping
    public ResponseEntity<Word> create(@Valid @RequestBody WordRequest request) { ... }
    
    @PutMapping("/{id}")
    public ResponseEntity<Word> update(@PathVariable Long id, @Valid @RequestBody WordRequest request) { ... }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { ... }
}
```

### Database and JPA

- Use constructor injection for dependencies
- Name repositories after the entity: `WordRepository`
- Use Spring Data JPA naming conventions for queries
- Use `@Transactional` on service methods that modify data

```java
@Repository
public interface WordRepository extends JpaRepository<Word, Long> {
    
    Optional<Word> findBySpelling(String spelling);
    
    List<Word> findByDifficultyOrderBySpelling(Difficulty difficulty);
    
    @Query("SELECT w FROM Word w WHERE w.length BETWEEN :min AND :max")
    List<Word> findByLengthRange(@Param("min") int min, @Param("max") int max);
    
    boolean existsBySpelling(String spelling);
}
```

### Logging

- Use SLF4J with Lombok's `@Slf4j` annotation
- Log at appropriate levels: `debug`, `info`, `warn`, `error`
- Include context in log messages
- Never log sensitive information (passwords, tokens)

```java
@Slf4j
@Service
public class WordService {
    
    public Word createWord(Word word) {
        log.debug("Creating word: {}", word.getSpelling());
        Word saved = wordRepository.save(word);
        log.info("Word created with id: {}", saved.getId());
        return saved;
    }
}
```

## Pre-commit Checklist

1. Run tests: `./mvnw test`
2. Run linting: `./mvnw checkstyle:check`
3. Check for unused imports and variables
4. Verify no hardcoded credentials or secrets
5. Update documentation if API changes

## File Organization

```
src/
├── main/
│   ├── java/com/example/words/
│   │   ├── controller/     # REST controllers
│   │   ├── service/        # Business logic
│   │   ├── repository/     # Data access
│   │   ├── model/          # JPA entities
│   │   ├── dto/            # Data transfer objects
│   │   ├── config/         # Configuration classes
│   │   ├── exception/      # Custom exceptions
│   │   └── util/           # Utility classes
│   └── resources/
│       └── application.yml
└── test/java/com/example/words/
    └── ...                 # Mirror main structure
```
