package com.example.words.service;

import com.example.words.dto.ClassroomResponse;
import com.example.words.dto.CreateClassroomRequest;
import com.example.words.dto.UpdateClassroomRequest;
import com.example.words.dto.UserResponse;
import com.example.words.exception.BadRequestException;
import com.example.words.exception.ResourceNotFoundException;
import com.example.words.model.AppUser;
import com.example.words.model.Classroom;
import com.example.words.model.ClassroomMember;
import com.example.words.model.UserRole;
import com.example.words.repository.ClassroomMemberRepository;
import com.example.words.repository.ClassroomRepository;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClassroomService {
    private static final int MAX_PAGE_SIZE = 100;

    private final ClassroomRepository classroomRepository;
    private final ClassroomMemberRepository classroomMemberRepository;
    private final UserService userService;

    public ClassroomService(
            ClassroomRepository classroomRepository,
            ClassroomMemberRepository classroomMemberRepository,
            UserService userService) {
        this.classroomRepository = classroomRepository;
        this.classroomMemberRepository = classroomMemberRepository;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public List<ClassroomResponse> findVisibleClassrooms(AppUser actor) {
        List<Classroom> classrooms = actor.getRole() == UserRole.ADMIN
                ? classroomRepository.findAll()
                : classroomRepository.findByTeacherId(actor.getId());

        return classrooms.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ClassroomResponse> findVisibleClassroomsPage(
            AppUser actor,
            int page,
            int size,
            String keyword,
            String sortBy,
            String sortDir) {
        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Specification<Classroom> specification = Specification.<Classroom>where(visibleTo(actor))
                .and(keywordLike(keyword));

        return classroomRepository.findAll(specification, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public ClassroomResponse createClassroom(CreateClassroomRequest request, AppUser actor) {
        Long teacherId = resolveTeacherId(request, actor);
        AppUser teacher = userService.getUserEntity(teacherId);
        if (teacher.getRole() != UserRole.TEACHER) {
            throw new BadRequestException("User is not a teacher: " + teacherId);
        }

        Classroom classroom = new Classroom();
        classroom.setName(request.getName().trim());
        classroom.setDescription(trimToNull(request.getDescription()));
        classroom.setTeacherId(teacherId);

        return toResponse(classroomRepository.save(classroom));
    }

    @Transactional
    public ClassroomResponse updateClassroom(Long classroomId, UpdateClassroomRequest request, AppUser actor) {
        Classroom classroom = getClassroomEntity(classroomId);
        ensureCanManageClassroom(actor, classroom);

        classroom.setName(request.getName().trim());
        classroom.setDescription(trimToNull(request.getDescription()));
        classroom.setTeacherId(resolveUpdatedTeacherId(request, classroom, actor));

        return toResponse(classroomRepository.save(classroom));
    }

    @Transactional
    public void deleteClassroom(Long classroomId, AppUser actor) {
        Classroom classroom = getClassroomEntity(classroomId);
        ensureCanManageClassroom(actor, classroom);
        classroomRepository.delete(classroom);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getStudentsForClassroom(Long classroomId, AppUser actor) {
        Classroom classroom = getClassroomEntity(classroomId);
        ensureCanManageClassroom(actor, classroom);

        return classroomMemberRepository.findByClassroomId(classroomId).stream()
                .map(ClassroomMember::getStudentId)
                .map(userService::findById)
                .toList();
    }

    @Transactional
    public void addStudentToClassroom(Long classroomId, Long studentId, AppUser actor) {
        Classroom classroom = getClassroomEntity(classroomId);
        ensureCanManageClassroom(actor, classroom);

        AppUser student = userService.getUserEntity(studentId);
        if (student.getRole() != UserRole.STUDENT) {
            throw new BadRequestException("User is not a student: " + studentId);
        }

        if (classroomMemberRepository.existsByClassroomIdAndStudentId(classroomId, studentId)) {
            return;
        }

        classroomMemberRepository.save(new ClassroomMember(null, classroomId, studentId, null));
    }

    @Transactional
    public void removeStudentFromClassroom(Long classroomId, Long studentId, AppUser actor) {
        Classroom classroom = getClassroomEntity(classroomId);
        ensureCanManageClassroom(actor, classroom);
        classroomMemberRepository.deleteByClassroomIdAndStudentId(classroomId, studentId);
    }

    @Transactional(readOnly = true)
    public Set<Long> getStudentIdsForClassrooms(Collection<Long> classroomIds, AppUser actor) {
        if (classroomIds == null || classroomIds.isEmpty()) {
            return Set.of();
        }

        List<Classroom> classrooms = classroomIds.stream()
                .map(this::getClassroomEntity)
                .peek(classroom -> ensureCanManageClassroom(actor, classroom))
                .toList();

        List<Long> validClassroomIds = classrooms.stream()
                .map(Classroom::getId)
                .toList();

        return classroomMemberRepository.findByClassroomIdIn(validClassroomIds).stream()
                .map(ClassroomMember::getStudentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public Classroom getClassroomEntity(Long classroomId) {
        return classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found: " + classroomId));
    }

    private ClassroomResponse toResponse(Classroom classroom) {
        AppUser teacher = userService.getUserEntity(classroom.getTeacherId());
        return new ClassroomResponse(
                classroom.getId(),
                classroom.getName(),
                classroom.getDescription(),
                classroom.getTeacherId(),
                teacher.getDisplayName(),
                classroomMemberRepository.countByClassroomId(classroom.getId()),
                classroom.getCreatedAt(),
                classroom.getUpdatedAt()
        );
    }

    private Long resolveTeacherId(CreateClassroomRequest request, AppUser actor) {
        if (actor.getRole() == UserRole.ADMIN) {
            if (request.getTeacherId() == null) {
                throw new BadRequestException("teacherId is required for admin");
            }
            return request.getTeacherId();
        }

        if (actor.getRole() == UserRole.TEACHER) {
            return actor.getId();
        }

        throw new AccessDeniedException("Only admin or teacher can manage classrooms");
    }

    private Long resolveUpdatedTeacherId(UpdateClassroomRequest request, Classroom classroom, AppUser actor) {
        if (actor.getRole() == UserRole.ADMIN) {
            Long teacherId = request.getTeacherId() != null ? request.getTeacherId() : classroom.getTeacherId();
            AppUser teacher = userService.getUserEntity(teacherId);
            if (teacher.getRole() != UserRole.TEACHER) {
                throw new BadRequestException("User is not a teacher: " + teacherId);
            }
            return teacherId;
        }

        if (actor.getRole() == UserRole.TEACHER) {
            if (request.getTeacherId() != null && !request.getTeacherId().equals(classroom.getTeacherId())) {
                throw new AccessDeniedException("Only admin can reassign classroom teacher");
            }
            return classroom.getTeacherId();
        }

        throw new AccessDeniedException("Only admin or teacher can manage classrooms");
    }

    private void ensureCanManageClassroom(AppUser actor, Classroom classroom) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER && actor.getId().equals(classroom.getTeacherId())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to manage this classroom");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        int normalizedPage = Math.max(page, 1) - 1;
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(direction, normalizeSortBy(sortBy)));
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "createdAt";
        }
        return switch (sortBy) {
            case "name" -> "name";
            case "updatedAt" -> "updatedAt";
            case "createdAt" -> "createdAt";
            default -> "createdAt";
        };
    }

    private Specification<Classroom> visibleTo(AppUser actor) {
        if (actor.getRole() == UserRole.ADMIN) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("teacherId"), actor.getId());
    }

    private Specification<Classroom> keywordLike(String keyword) {
        String normalizedKeyword = trimToNull(keyword);
        if (normalizedKeyword == null) {
            return null;
        }
        String pattern = "%" + normalizedKeyword.toLowerCase(Locale.ROOT) + "%";
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), pattern)
        );
    }
}
