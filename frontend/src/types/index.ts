export interface MetaWord {
  id: number;
  word: string;
  phonetic?: string;
  definition?: string;
  partOfSpeech?: string;
  exampleSentence?: string;
  translation?: string;
  difficulty?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface MetaWordEntry {
  word: string;
  phonetic?: string;
  definition?: string;
  partOfSpeech?: string;
  exampleSentence?: string;
  translation?: string;
  difficulty?: number;
}

export interface Dictionary {
  id: number;
  name: string;
  filePath?: string;
  fileSize?: number;
  category?: string;
  wordCount?: number;
  creationType?: 'USER_CREATED' | 'IMPORTED';
  createdBy?: number;
  ownerUserId?: number;
  scopeType?: 'SYSTEM' | 'TEACHER' | 'PERSONAL';
  createdAt?: string;
  updatedAt?: string;
}

export interface Classroom {
  id: number;
  name: string;
  description?: string | null;
  teacherId: number;
  teacherName?: string | null;
  studentCount: number;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export type UserRole = 'ADMIN' | 'TEACHER' | 'STUDENT';

export type UserStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED';

export interface User {
  id: number;
  username: string;
  displayName: string;
  email?: string | null;
  phone?: string | null;
  role: UserRole;
  status: UserStatus;
  createdAt?: string | null;
  updatedAt?: string | null;
  lastLoginAt?: string | null;
}

export interface FamousQuote {
  text: string;
  translation: string;
  author: string;
}

export interface LoginResponse {
  token: string;
  user: User;
  quote: FamousQuote;
}

export interface DictionaryWord {
  id: number;
  dictionaryId: number;
  metaWordId: number;
  createdAt?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ExamOption {
  key: string;
  translation: string;
}

export interface ExamQuestion {
  questionId: number;
  word: string;
  options: ExamOption[];
}

export interface Exam {
  examId: number;
  dictionaryId: number;
  dictionaryName: string;
  questionCount: number;
  answeredCount: number;
  correctCount: number;
  score: number;
  status: 'GENERATED' | 'SUBMITTED';
  createdAt?: string;
  submittedAt?: string;
  questions: ExamQuestion[];
}

export interface ExamAnswer {
  questionId: number;
  selectedOption: string;
}

export interface ExamResultQuestion {
  questionId: number;
  word: string;
  selectedOption?: string;
  selectedTranslation?: string;
  correctOption: string;
  correctTranslation: string;
  correct: boolean;
}

export interface ExamSubmissionResult {
  examId: number;
  dictionaryId: number;
  dictionaryName: string;
  totalQuestions: number;
  answeredQuestions: number;
  correctCount: number;
  score: number;
  status: 'SUBMITTED';
  submittedAt?: string;
  results: ExamResultQuestion[];
}

export interface ExamHistoryItem {
  examId: number;
  dictionaryId: number;
  dictionaryName: string;
  questionCount: number;
  answeredCount: number;
  correctCount: number;
  score: number;
  status: 'SUBMITTED';
  createdAt?: string;
  submittedAt?: string;
}
