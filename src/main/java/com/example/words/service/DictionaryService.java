package com.example.words.service;

import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.Dictionary;
import com.example.words.model.DictionaryCreationType;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomRepository;
import com.example.words.repository.DictionaryRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DictionaryService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryService.class);
    private static final String TRANSLATION_DIR = "/app/books";

    private final DictionaryRepository dictionaryRepository;
    private final DictionaryAssignmentService dictionaryAssignmentService;
    private final ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService;
    private final AccessControlService accessControlService;
    private final ClassroomRepository classroomRepository;

    public DictionaryService(
            DictionaryRepository dictionaryRepository,
            DictionaryAssignmentService dictionaryAssignmentService,
            ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService,
            AccessControlService accessControlService,
            ClassroomRepository classroomRepository) {
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryAssignmentService = dictionaryAssignmentService;
        this.classroomDictionaryAssignmentService = classroomDictionaryAssignmentService;
        this.accessControlService = accessControlService;
        this.classroomRepository = classroomRepository;
    }

    public List<Dictionary> findAll() {
        return dictionaryRepository.findAll();
    }

    public List<Dictionary> findVisibleDictionaries(AppUser actor) {
        List<Dictionary> dictionaries = dictionaryRepository.findAll();
        if (actor.getRole() == UserRole.ADMIN) {
            return dictionaries;
        }

        if (actor.getRole() == UserRole.TEACHER) {
            Set<Long> assignedDictionaryIds = classroomDictionaryAssignmentService.getAssignedDictionaryIdsForTeacher(actor.getId());
            return dictionaries.stream()
                    .filter(dictionary -> dictionary.getScopeType() == ResourceScopeType.SYSTEM
                            || actor.getId().equals(dictionary.getOwnerUserId())
                            || actor.getId().equals(dictionary.getCreatedBy())
                            || assignedDictionaryIds.contains(dictionary.getId()))
                    .toList();
        }

        Set<Long> assignedDictionaryIds = new LinkedHashSet<>(
                dictionaryAssignmentService.getAssignedDictionaryIdsForStudent(actor.getId())
        );
        assignedDictionaryIds.addAll(classroomDictionaryAssignmentService.getAssignedDictionaryIdsForStudent(actor.getId()));
        return dictionaries.stream()
                .filter(dictionary -> dictionary.getScopeType() == ResourceScopeType.SYSTEM
                        || assignedDictionaryIds.contains(dictionary.getId()))
                .toList();
    }

    public List<Dictionary> findAssignedDictionariesForStudent(Long studentId) {
        Set<Long> assignedDictionaryIds = new LinkedHashSet<>(
                dictionaryAssignmentService.getAssignedDictionaryIdsForStudent(studentId)
        );
        assignedDictionaryIds.addAll(classroomDictionaryAssignmentService.getAssignedDictionaryIdsForStudent(studentId));
        return dictionaryRepository.findAll().stream()
                .filter(dictionary -> assignedDictionaryIds.contains(dictionary.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Dictionary> findVisibleDictionariesForClassrooms(Collection<Long> classroomIds, AppUser actor) {
        if (classroomIds == null || classroomIds.isEmpty()) {
            return findVisibleDictionaries(actor);
        }

        List<Classroom> classrooms = resolveVisibleClassrooms(classroomIds, actor);
        Set<Long> dictionaryIds = classroomDictionaryAssignmentService.intersectAssignedDictionaryIdsForClassrooms(
                classrooms.stream().map(Classroom::getId).toList()
        );
        if (dictionaryIds.isEmpty()) {
            return List.of();
        }

        return findVisibleDictionaries(actor).stream()
                .filter(dictionary -> dictionaryIds.contains(dictionary.getId()))
                .toList();
    }

    public Optional<Dictionary> findById(Long id) {
        return dictionaryRepository.findById(id);
    }

    public Optional<Dictionary> findByIdVisibleToUser(Long id, AppUser actor) {
        return dictionaryRepository.findById(id)
                .filter(dictionary -> {
                    try {
                        accessControlService.ensureCanViewDictionary(actor, dictionary);
                        return true;
                    } catch (org.springframework.security.access.AccessDeniedException ex) {
                        return false;
                    }
                });
    }

    public Optional<Dictionary> findByName(String name) {
        return dictionaryRepository.findByName(name);
    }

    public List<Dictionary> findByCategory(String category) {
        return dictionaryRepository.findByCategory(category);
    }

    public List<Dictionary> findByCategoryVisibleToUser(String category, AppUser actor) {
        return findVisibleDictionaries(actor).stream()
                .filter(dictionary -> category.equals(dictionary.getCategory()))
                .toList();
    }

    @Transactional
    public Dictionary save(Dictionary dictionary) {
        return dictionaryRepository.save(dictionary);
    }

    @Transactional
    public Dictionary createDictionary(Dictionary dictionary, AppUser actor) {
        dictionary.setId(null);
        dictionary.setCreationType(DictionaryCreationType.USER_CREATED);
        dictionary.setCreatedBy(actor.getId());
        dictionary.setOwnerUserId(actor.getId());
        dictionary.setScopeType(actor.getRole() == UserRole.ADMIN ? ResourceScopeType.SYSTEM : ResourceScopeType.TEACHER);
        return dictionaryRepository.save(dictionary);
    }

    @Transactional
    public int importFromDirectory() {
        java.io.File dir = new java.io.File(TRANSLATION_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            log.error("Directory not found: {}", TRANSLATION_DIR);
            return 0;
        }

        java.io.File[] files = dir.listFiles();
        if (files == null) {
            log.warn("No files found in directory: {}", TRANSLATION_DIR);
            return 0;
        }

        int count = 0;
        for (java.io.File file : files) {
            if (file.isFile() && file.getName().endsWith(".csv")) {
                if (!dictionaryRepository.existsByName(file.getName())) {
                    String category = extractCategory(file.getName());
                    Dictionary dictionary = new Dictionary(
                            file.getName(),
                            file.getAbsolutePath(),
                            file.length(),
                            category,
                            DictionaryCreationType.IMPORTED
                    );
                    dictionaryRepository.save(dictionary);
                    count++;
                    log.info("Imported dictionary: {}", file.getName());
                }
            }
        }

        log.info("Total dictionaries imported: {}", count);
        return count;
    }

    @Transactional
    public void updateWordCount(Long dictionaryId, int wordCount) {
        dictionaryRepository.findById(dictionaryId).ifPresent(dictionary -> {
            dictionary.setWordCount(wordCount);
            if (dictionary.getEntryCount() == null) {
                dictionary.setEntryCount(wordCount);
            }
            dictionaryRepository.save(dictionary);
        });
    }

    @Transactional
    public void updateCounts(Long dictionaryId, int wordCount, int entryCount) {
        dictionaryRepository.updateCounts(dictionaryId, wordCount, entryCount);
    }

    @Transactional
    public void updateEntryCount(Long dictionaryId, int entryCount) {
        dictionaryRepository.findById(dictionaryId).ifPresent(dictionary -> {
            dictionary.setEntryCount(entryCount);
            dictionaryRepository.save(dictionary);
        });
    }

    @Transactional
    public void incrementWordCount(Long dictionaryId, int delta) {
        if (delta <= 0) {
            return;
        }
        dictionaryRepository.incrementWordCount(dictionaryId, delta);
    }

    @Transactional
    public void deleteAll() {
        dictionaryRepository.deleteAll();
    }

    @Transactional
    public int deleteUserCreatedDictionaries() {
        int deletedCount = dictionaryRepository.deleteByCreationType(DictionaryCreationType.USER_CREATED);
        log.info("Deleted {} user-created dictionaries", deletedCount);
        return deletedCount;
    }

    @Transactional
    public boolean deleteById(Long id) {
        return dictionaryRepository.findById(id)
                .map(dictionary -> {
                    if (dictionary.getCreationType() == DictionaryCreationType.USER_CREATED) {
                        dictionaryRepository.delete(dictionary);
                        log.info("Deleted user-created dictionary: {} (ID: {})", dictionary.getName(), id);
                        return true;
                    } else {
                        log.warn("Cannot delete imported dictionary: {} (ID: {})", dictionary.getName(), id);
                        return false;
                    }
                })
                .orElse(false);
    }

    @Transactional
    public boolean deleteById(Long id, AppUser actor) {
        return dictionaryRepository.findById(id)
                .map(dictionary -> {
                    accessControlService.ensureCanManageDictionary(actor, dictionary);
                    dictionaryRepository.delete(dictionary);
                    log.info("Deleted dictionary: {} (ID: {}) by user {}", dictionary.getName(), id, actor.getId());
                    return true;
                })
                .orElse(false);
    }

    public String extractCategory(String fileName) {
        if (fileName.contains("高考")) {
            return "高考";
        } else if (fileName.contains("考研")) {
            return "考研";
        } else if (fileName.contains("四级")) {
            return "四级";
        } else if (fileName.contains("六级")) {
            return "六级";
        } else if (fileName.contains("雅思")) {
            return "雅思";
        } else if (fileName.contains("托福") || fileName.contains("TOEFL")) {
            return "托福";
        } else if (fileName.contains("GRE")) {
            return "GRE";
        } else if (fileName.contains("中考")) {
            return "中考";
        } else if (fileName.contains("专四")) {
            return "专四";
        } else if (fileName.contains("专八")) {
            return "专八";
        } else if (fileName.contains("初中")) {
            return "初中";
        } else if (fileName.contains("高中")) {
            return "高中";
        } else if (fileName.contains("小学")) {
            return "小学";
        } else if (fileName.contains("BEC")) {
            return "BEC";
        } else if (fileName.contains("PETS")) {
            return "PETS";
        } else if (fileName.contains("SAT")) {
            return "SAT";
        } else if (fileName.contains("GMAT")) {
            return "GMAT";
        } else if (fileName.contains("IELTS")) {
            return "雅思";
        } else if (fileName.contains("MBA")) {
            return "MBA";
        } else if (fileName.contains("考博")) {
            return "考博";
        }
        return "其他";
    }

    private List<Classroom> resolveVisibleClassrooms(Collection<Long> classroomIds, AppUser actor) {
        List<Classroom> classrooms = new ArrayList<>();
        for (Long classroomId : classroomIds.stream().distinct().toList()) {
            Classroom classroom = classroomRepository.findById(classroomId)
                    .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomId));
            if (actor.getRole() == UserRole.TEACHER && !actor.getId().equals(classroom.getTeacherId())) {
                throw new AccessDeniedException("You do not have permission to manage classroom " + classroom.getId());
            }
            if (actor.getRole() == UserRole.STUDENT) {
                throw new AccessDeniedException("Students cannot filter dictionaries by classroom");
            }
            classrooms.add(classroom);
        }
        return classrooms;
    }
}
