package com.example.words.service;

import com.example.words.model.AiConfig;
import com.example.words.model.AppUser;
import com.example.words.model.Dictionary;
import com.example.words.model.Exam;
import com.example.words.model.ResourceScopeType;
import com.example.words.model.UserRole;
import com.example.words.model.VideoAsset;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    private final DictionaryAssignmentService dictionaryAssignmentService;
    private final ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService;
    private final TeacherStudentService teacherStudentService;

    public AccessControlService(
            DictionaryAssignmentService dictionaryAssignmentService,
            ClassroomDictionaryAssignmentService classroomDictionaryAssignmentService,
            TeacherStudentService teacherStudentService) {
        this.dictionaryAssignmentService = dictionaryAssignmentService;
        this.classroomDictionaryAssignmentService = classroomDictionaryAssignmentService;
        this.teacherStudentService = teacherStudentService;
    }

    public void ensureAdmin(AppUser actor) {
        if (actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Admin role is required");
        }
    }

    public void ensureAdminOrSelf(AppUser actor, Long userId) {
        if (actor.getRole() == UserRole.ADMIN || actor.getId().equals(userId)) {
            return;
        }
        throw new AccessDeniedException("You can only access your own user profile");
    }

    public void ensureCanManageAiConfig(AppUser actor, AiConfig config) {
        if (!actor.getId().equals(config.getUserId())) {
            throw new AccessDeniedException("You do not have permission to access this AI config");
        }
    }

    public void ensureCanViewDictionary(AppUser actor, Dictionary dictionary) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER) {
            if (dictionary.getScopeType() == ResourceScopeType.SYSTEM
                    || actor.getId().equals(dictionary.getOwnerUserId())
                    || actor.getId().equals(dictionary.getCreatedBy())
                    || classroomDictionaryAssignmentService.isDictionaryAssignedToTeacherClassrooms(
                            dictionary.getId(),
                            actor.getId()
                    )) {
                return;
            }
        }

        if (actor.getRole() == UserRole.STUDENT) {
            if (dictionary.getScopeType() == ResourceScopeType.SYSTEM
                    || dictionaryAssignmentService.isDictionaryAssignedToStudent(dictionary.getId(), actor.getId())
                    || classroomDictionaryAssignmentService.isDictionaryAssignedToStudent(dictionary.getId(), actor.getId())) {
                return;
            }
        }

        throw new AccessDeniedException("You do not have access to this dictionary");
    }

    public void ensureCanManageDictionary(AppUser actor, Dictionary dictionary) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER
                && dictionary.getScopeType() != ResourceScopeType.SYSTEM
                && actor.getId().equals(dictionary.getOwnerUserId())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to manage this dictionary");
    }

    public void ensureCanAssignDictionaryToStudent(AppUser actor, Dictionary dictionary, Long studentId) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER
                && (dictionary.getScopeType() == ResourceScopeType.SYSTEM
                || actor.getId().equals(dictionary.getOwnerUserId()))
                && teacherStudentService.isTeacherResponsibleForStudent(actor.getId(), studentId)) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to assign this dictionary");
    }

    public void ensureCanCreateExamForStudent(AppUser actor, Long targetUserId) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER
                && teacherStudentService.isTeacherResponsibleForStudent(actor.getId(), targetUserId)) {
            return;
        }

        throw new AccessDeniedException("You cannot create exams for this student");
    }

    public void ensureCanViewExam(AppUser actor, Exam exam) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER) {
            if (actor.getId().equals(exam.getCreatedByUserId())
                    || teacherStudentService.isTeacherResponsibleForStudent(actor.getId(), exam.getTargetUserId())) {
                return;
            }
        }

        if (actor.getRole() == UserRole.STUDENT && actor.getId().equals(exam.getTargetUserId())) {
            return;
        }

        throw new AccessDeniedException("You do not have access to this exam");
    }

    public void ensureCanSubmitExam(AppUser actor, Exam exam) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.STUDENT && actor.getId().equals(exam.getTargetUserId())) {
            return;
        }

        throw new AccessDeniedException("Only the assigned student can submit this exam");
    }

    public void ensureCanViewVideo(AppUser actor, VideoAsset videoAsset) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER) {
            if (videoAsset.getScopeType() == ResourceScopeType.SYSTEM
                    || actor.getId().equals(videoAsset.getOwnerUserId())
                    || actor.getId().equals(videoAsset.getCreatedBy())) {
                return;
            }
        }

        throw new AccessDeniedException("You do not have access to this video");
    }

    public void ensureCanManageVideo(AppUser actor, VideoAsset videoAsset) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getRole() == UserRole.TEACHER
                && videoAsset.getScopeType() != ResourceScopeType.SYSTEM
                && actor.getId().equals(videoAsset.getOwnerUserId())) {
            return;
        }

        throw new AccessDeniedException("You do not have permission to manage this video");
    }
}
