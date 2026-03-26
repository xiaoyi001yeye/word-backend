import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  authApi,
  clearStoredToken,
  dictionaryApi,
  dictionaryWordApi,
  examApi,
  metaWordApi,
  setUnauthorizedHandler,
  storeToken,
  studentApi,
  teacherApi,
  userApi,
} from './api';
import type {
  Dictionary,
  Exam,
  ExamHistoryItem,
  ExamSubmissionResult,
  MetaWord,
  User,
} from './types';
import { AddWordListModal } from './components/AddWordListModal';
import { CreateDictionaryModal } from './components/CreateDictionaryModal';
import { CreateExamModal } from './components/CreateExamModal';
import { CsvImportModal } from './components/CsvImportModal';
import { DictionaryCard } from './components/DictionaryCard';
import { ExamHistoryModal } from './components/ExamHistoryModal';
import { ExamSessionModal } from './components/ExamSessionModal';
import { LoginScreen } from './components/LoginScreen';
import { SearchBox } from './components/SearchBox';
import { WordDetail } from './components/WordDetail';
import { WordList } from './components/WordList';
import './App.css';

type MobilePanel = 'library' | 'words';

function App() {
  const [authChecking, setAuthChecking] = useState(true);
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [currentUser, setCurrentUser] = useState<User | null>(null);

  const [dictionaries, setDictionaries] = useState<Dictionary[]>([]);
  const [availableStudents, setAvailableStudents] = useState<User[]>([]);
  const [selectedDictionary, setSelectedDictionary] = useState<Dictionary | null>(null);
  const [metaWords, setMetaWords] = useState<MetaWord[]>([]);
  const [selectedWord, setSelectedWord] = useState<MetaWord | null>(null);
  const [loading, setLoading] = useState(false);
  const [dictSearchQuery, setDictSearchQuery] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [isSearching, setIsSearching] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [dictPage, setDictPage] = useState(1);
  const [wordPage, setWordPage] = useState(1);
  const [totalWords, setTotalWords] = useState(0);
  const [isCompact, setIsCompact] = useState(false);
  const [mobilePanel, setMobilePanel] = useState<MobilePanel>('library');
  const [showMobileDetail, setShowMobileDetail] = useState(false);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showAddWordListModal, setShowAddWordListModal] = useState(false);
  const [showCsvImportModal, setShowCsvImportModal] = useState(false);
  const [showExamSetupModal, setShowExamSetupModal] = useState(false);
  const [showExamHistoryModal, setShowExamHistoryModal] = useState(false);
  const [dictionaryForAdd, setDictionaryForAdd] = useState<Dictionary | null>(null);
  const [dictionaryForCsvImport, setDictionaryForCsvImport] = useState<Dictionary | null>(null);
  const [isImportingDictionaries, setIsImportingDictionaries] = useState(false);
  const [importFeedback, setImportFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const [examLoading, setExamLoading] = useState(false);
  const [examError, setExamError] = useState<string | null>(null);
  const [examHistory, setExamHistory] = useState<ExamHistoryItem[]>([]);
  const [activeExam, setActiveExam] = useState<Exam | null>(null);
  const [examAnswers, setExamAnswers] = useState<Record<number, string>>({});
  const [examResult, setExamResult] = useState<ExamSubmissionResult | null>(null);

  const DICT_PAGE_SIZE = 6;
  const WORD_PAGE_SIZE = 10;

  const isAdmin = currentUser?.role === 'ADMIN';
  const isTeacher = currentUser?.role === 'TEACHER';
  const isStudent = currentUser?.role === 'STUDENT';
  const canManageWorkspace = isAdmin || isTeacher;
  const canImportSystemDictionaries = isAdmin;
  const canCreateExam = (isAdmin || isTeacher) && availableStudents.length > 0;

  const resetWorkspace = useCallback(() => {
    setDictionaries([]);
    setAvailableStudents([]);
    setSelectedDictionary(null);
    setMetaWords([]);
    setSelectedWord(null);
    setSearchKeyword('');
    setIsSearching(false);
    setDictSearchQuery('');
    setDictPage(1);
    setWordPage(1);
    setTotalWords(0);
    setSidebarCollapsed(false);
    setMobilePanel('library');
    setShowMobileDetail(false);
    setShowCreateModal(false);
    setShowAddWordListModal(false);
    setShowCsvImportModal(false);
    setShowExamSetupModal(false);
    setShowExamHistoryModal(false);
    setDictionaryForAdd(null);
    setDictionaryForCsvImport(null);
    setImportFeedback(null);
    setExamLoading(false);
    setExamError(null);
    setExamHistory([]);
    setActiveExam(null);
    setExamAnswers({});
    setExamResult(null);
  }, []);

  const handleSignOut = useCallback(() => {
    clearStoredToken();
    setCurrentUser(null);
    setAuthError(null);
    resetWorkspace();
  }, [resetWorkspace]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      handleSignOut();
      setAuthError('登录状态已失效，请重新登录。');
    });

    return () => {
      setUnauthorizedHandler(null);
    };
  }, [handleSignOut]);

  useEffect(() => {
    let mounted = true;

    const bootstrap = async () => {
      try {
        const user = await authApi.me();
        if (mounted) {
          setCurrentUser(user);
          setAuthError(null);
        }
      } catch {
        if (mounted) {
          clearStoredToken();
          setCurrentUser(null);
        }
      } finally {
        if (mounted) {
          setAuthChecking(false);
        }
      }
    };

    bootstrap();

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return undefined;
    }

    const mediaQuery = window.matchMedia('(max-width: 900px)');
    const updateCompactState = () => setIsCompact(mediaQuery.matches);
    updateCompactState();
    mediaQuery.addEventListener('change', updateCompactState);

    return () => mediaQuery.removeEventListener('change', updateCompactState);
  }, []);

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    let mounted = true;

    const loadAvailableStudents = async () => {
      try {
        const students = isAdmin
          ? (await userApi.getAll()).filter((user) => user.role === 'STUDENT')
          : isTeacher
            ? await teacherApi.getMyStudents()
            : [];

        if (mounted) {
          setAvailableStudents(students);
        }
      } catch (error) {
        if (mounted) {
          console.error('Failed to load students:', error);
        }
      }
    };

    loadAvailableStudents();

    return () => {
      mounted = false;
    };
  }, [currentUser, isAdmin, isTeacher]);

  const loadDictionaries = useCallback(async () => {
    if (!currentUser) {
      return;
    }

    setLoading(true);
    try {
      const nextDictionaries = isStudent
        ? await studentApi.getMyDictionaries()
        : await dictionaryApi.getAll();
      setDictionaries(nextDictionaries);
      setImportFeedback(null);
    } catch (error) {
      console.error('Failed to load dictionaries:', error);
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '加载辞书失败',
      });
    } finally {
      setLoading(false);
    }
  }, [currentUser, isStudent]);

  useEffect(() => {
    if (!currentUser) {
      return;
    }
    loadDictionaries();
  }, [currentUser, loadDictionaries]);

  useEffect(() => {
    if (!selectedDictionary && dictionaries.length > 0 && !isSearching) {
      setSelectedDictionary(dictionaries[0]);
    }
  }, [dictionaries, selectedDictionary, isSearching]);

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    let mounted = true;

    const loadWords = async () => {
      if (!selectedDictionary && !isSearching) {
        setMetaWords([]);
        setTotalWords(0);
        return;
      }

      setLoading(true);
      try {
        if (isSearching && searchKeyword.trim()) {
          const pageResult = await metaWordApi.search(searchKeyword.trim(), undefined, wordPage - 1, WORD_PAGE_SIZE);
          if (!mounted) {
            return;
          }
          setMetaWords(pageResult.content);
          setTotalWords(pageResult.totalElements);
          return;
        }

        if (!selectedDictionary) {
          return;
        }

        const pageResult = await dictionaryWordApi.getWordsByDictionary(selectedDictionary.id, wordPage, WORD_PAGE_SIZE);
        if (!mounted) {
          return;
        }

        setMetaWords(pageResult.content);
        setTotalWords(pageResult.totalElements);
      } catch (error) {
        if (mounted) {
          console.error('Failed to load words:', error);
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    loadWords();

    return () => {
      mounted = false;
    };
  }, [currentUser, isSearching, searchKeyword, selectedDictionary, wordPage]);

  useEffect(() => {
    if (!isCompact) {
      setShowMobileDetail(false);
      return;
    }

    if (selectedWord) {
      setShowMobileDetail(true);
    }
  }, [isCompact, selectedWord]);

  const filteredDictionaries = useMemo(
    () => dictionaries.filter((dictionary) =>
      dictionary.name.toLowerCase().includes(dictSearchQuery.toLowerCase()),
    ),
    [dictSearchQuery, dictionaries],
  );

  const paginatedDictionaries = useMemo(
    () => filteredDictionaries.slice((dictPage - 1) * DICT_PAGE_SIZE, dictPage * DICT_PAGE_SIZE),
    [dictPage, filteredDictionaries],
  );

  const totalDictPages = Math.max(1, Math.ceil(filteredDictionaries.length / DICT_PAGE_SIZE));
  const totalWordPages = Math.max(1, Math.ceil(totalWords / WORD_PAGE_SIZE));
  const workspaceLabel = isSearching
    ? '全库搜索'
    : selectedDictionary?.name || '词汇总览';
  const workspaceMeta = isSearching
    ? searchKeyword
      ? `关键词「${searchKeyword}」`
      : '输入关键词开始搜索'
    : selectedDictionary?.category || '选择一本辞书开始浏览';
  const activeWordCount = isSearching ? totalWords : selectedDictionary?.wordCount || totalWords;

  const canManageDictionary = useCallback((dictionary: Dictionary) => {
    if (!currentUser) {
      return false;
    }
    if (isAdmin) {
      return true;
    }
    if (isTeacher) {
      return dictionary.creationType === 'USER_CREATED';
    }
    return false;
  }, [currentUser, isAdmin, isTeacher]);

  const handleLogin = useCallback(async (username: string, password: string) => {
    setAuthLoading(true);
    setAuthError(null);
    try {
      const response = await authApi.login(username, password);
      storeToken(response.token);
      setCurrentUser(response.user);
      resetWorkspace();
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : '登录失败');
    } finally {
      setAuthLoading(false);
      setAuthChecking(false);
    }
  }, [resetWorkspace]);

  const handleDictionaryCreated = useCallback((newDictionary: Dictionary) => {
    setDictionaries((prev) => [newDictionary, ...prev]);
    setSelectedDictionary(newDictionary);
    setSelectedWord(null);
    setIsSearching(false);
    setSearchKeyword('');
    setWordPage(1);
    setMobilePanel('words');
    setShowMobileDetail(false);
    setShowCreateModal(false);
  }, []);

  const handleDeleteDictionary = useCallback(async (id: number) => {
    if (!window.confirm('确定要删除这个辞书吗？此操作无法撤销。')) {
      return;
    }

    try {
      await dictionaryApi.deleteById(id);
      setDictionaries((prev) => prev.filter((dictionary) => dictionary.id !== id));
      if (selectedDictionary?.id === id) {
        setSelectedDictionary(null);
        setSelectedWord(null);
        setMetaWords([]);
      }
    } catch (error) {
      console.error('Failed to delete dictionary:', error);
      alert(error instanceof Error ? error.message : '删除辞书失败，请重试。');
    }
  }, [selectedDictionary]);

  const handleImportDictionaries = useCallback(async () => {
    if (!canImportSystemDictionaries || isImportingDictionaries) {
      return;
    }

    setIsImportingDictionaries(true);
    setImportFeedback(null);
    try {
      const result = await dictionaryApi.importDictionaries();
      await loadDictionaries();
      setImportFeedback({
        type: 'success',
        message: `导入完成，本次新增 ${result.count} 本辞书。`,
      });
    } catch (error) {
      setImportFeedback({
        type: 'error',
        message: error instanceof Error ? error.message : '导入失败',
      });
    } finally {
      setIsImportingDictionaries(false);
    }
  }, [canImportSystemDictionaries, isImportingDictionaries, loadDictionaries]);

  const handleSearchClear = useCallback(() => {
    setIsSearching(false);
    setSearchKeyword('');
    setWordPage(1);
    setSelectedWord(null);
    setMobilePanel('words');
  }, []);

  const handleSearchQueryChange = useCallback((query: string) => {
    setSearchKeyword(query);
    setWordPage(1);
    setSelectedWord(null);
    setMobilePanel('words');
    setIsSearching(Boolean(query.trim()));
  }, []);

  const handleSelectDictionary = useCallback((dictionary: Dictionary) => {
    setSelectedDictionary(dictionary);
    setSelectedWord(null);
    setWordPage(1);
    setMobilePanel('words');
    setShowMobileDetail(false);
    setIsSearching(false);
    setSearchKeyword('');
  }, []);

  const handleSelectWord = useCallback((word: MetaWord) => {
    setSelectedWord(word);
    if (isCompact) {
      setShowMobileDetail(true);
    }
  }, [isCompact]);

  const handleOpenExamHistory = useCallback(async () => {
    if (!selectedDictionary) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    setShowExamHistoryModal(true);
    try {
      const historyItems = await examApi.getHistory(selectedDictionary.id);
      setExamHistory(historyItems);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '加载考试历史失败');
    } finally {
      setExamLoading(false);
    }
  }, [selectedDictionary]);

  const handleOpenExamSetup = useCallback(() => {
    if (!canCreateExam || !selectedDictionary) {
      return;
    }
    setExamError(null);
    setShowExamSetupModal(true);
  }, [canCreateExam, selectedDictionary]);

  const handleStartExam = useCallback(async (questionCount: number, targetUserId: number) => {
    if (!selectedDictionary) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    try {
      const exam = await examApi.create(selectedDictionary.id, questionCount, targetUserId);
      setActiveExam(exam);
      setExamAnswers({});
      setExamResult(null);
      setShowExamSetupModal(false);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '生成考试失败');
    } finally {
      setExamLoading(false);
    }
  }, [selectedDictionary]);

  const handleSelectExamOption = useCallback((questionId: number, optionKey: string) => {
    setExamAnswers((prev) => ({
      ...prev,
      [questionId]: optionKey,
    }));
  }, []);

  const handleSubmitExam = useCallback(async () => {
    if (!activeExam) {
      return;
    }

    setExamLoading(true);
    setExamError(null);
    try {
      const answers = Object.entries(examAnswers).map(([questionId, selectedOption]) => ({
        questionId: Number(questionId),
        selectedOption,
      }));

      const result = await examApi.submit(activeExam.examId, answers);
      setExamResult(result);

      if (selectedDictionary) {
        const historyItems = await examApi.getHistory(selectedDictionary.id);
        setExamHistory(historyItems);
      }
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '提交考试失败');
    } finally {
      setExamLoading(false);
    }
  }, [activeExam, examAnswers, selectedDictionary]);

  const handleViewHistoryResult = useCallback(async (examId: number) => {
    setExamLoading(true);
    setExamError(null);
    try {
      const [exam, result] = await Promise.all([
        examApi.getById(examId),
        examApi.getResult(examId),
      ]);
      setActiveExam(exam);
      setExamResult(result);
      setExamAnswers({});
      setShowExamHistoryModal(false);
    } catch (error) {
      setExamError(error instanceof Error ? error.message : '加载考试详情失败');
    } finally {
      setExamLoading(false);
    }
  }, []);

  const handleCloseExam = useCallback(() => {
    setActiveExam(null);
    setExamAnswers({});
    setExamResult(null);
    setExamError(null);
  }, []);

  if (authChecking) {
    return (
      <div className="auth-loading">
        <span className="sidebar__spinner"></span>
        <p>正在恢复登录状态...</p>
      </div>
    );
  }

  if (!currentUser) {
    return <LoginScreen loading={authLoading} error={authError} onSubmit={handleLogin} />;
  }

  const sidebarContent = (
    <div className="sidebar__section">
      <div className="sidebar__header">
        <div className="sidebar__header-copy">
          <p className="sidebar__eyebrow">Library</p>
          <h2 className="sidebar__title">辞书书架</h2>
          <p className="sidebar__summary">
            {filteredDictionaries.length} / {dictionaries.length} 本可用辞书
          </p>
        </div>

        {canManageWorkspace && (
          <div className="sidebar__actions sidebar__actions--stacked">
            {canImportSystemDictionaries && (
              <button
                className="exam-history-btn"
                onClick={handleImportDictionaries}
                disabled={isImportingDictionaries}
              >
                {isImportingDictionaries ? '导入中...' : '导入 books'}
              </button>
            )}
            <button className="add-dictionary-btn" onClick={() => setShowCreateModal(true)}>
              新建辞书
            </button>
          </div>
        )}
      </div>

      <div className="sidebar__toolbar">
        <div className="sidebar__search">
          <input
            type="text"
            className="sidebar__search-input"
            placeholder="筛选辞书名称"
            value={dictSearchQuery}
            onChange={(event) => {
              setDictSearchQuery(event.target.value);
              setDictPage(1);
            }}
          />
          {dictSearchQuery && (
            <button className="sidebar__search-clear" onClick={() => setDictSearchQuery('')}>
              ×
            </button>
          )}
        </div>
        <p className="sidebar__hint">
          {isStudent
            ? '这里展示分配给你的学习资源。'
            : '选择一本辞书后，右侧会同步单词、考试与详情工作区。'}
        </p>
      </div>

      {loading && dictionaries.length === 0 ? (
        <div className="sidebar__loading">
          <span className="sidebar__spinner"></span>
        </div>
      ) : filteredDictionaries.length === 0 ? (
        <div className="sidebar__empty">
          <p>还没有匹配的辞书</p>
        </div>
      ) : (
        <div className="sidebar__list">
          {paginatedDictionaries.map((dictionary, index) => (
            <div key={dictionary.id} style={{ animationDelay: `${index * 40}ms` }}>
              <DictionaryCard
                dictionary={dictionary}
                isSelected={selectedDictionary?.id === dictionary.id}
                onClick={() => handleSelectDictionary(dictionary)}
                onDelete={canManageDictionary(dictionary) ? () => handleDeleteDictionary(dictionary.id) : undefined}
                onAddJson={canManageDictionary(dictionary) ? () => {
                  setDictionaryForAdd(dictionary);
                  setShowAddWordListModal(true);
                } : undefined}
                onImportCsv={canManageDictionary(dictionary) ? () => {
                  setDictionaryForCsvImport(dictionary);
                  setShowCsvImportModal(true);
                } : undefined}
              />
            </div>
          ))}
        </div>
      )}

      {totalDictPages > 1 && (
        <div className="sidebar__pagination">
          <button
            className="sidebar__page-btn"
            disabled={dictPage === 1}
            onClick={() => setDictPage((prev) => prev - 1)}
          >
            ‹
          </button>
          <span className="sidebar__page-info">{dictPage} / {totalDictPages}</span>
          <button
            className="sidebar__page-btn"
            disabled={dictPage === totalDictPages}
            onClick={() => setDictPage((prev) => prev + 1)}
          >
            ›
          </button>
        </div>
      )}
    </div>
  );

  const listPanel = (
    <div className="content__panel content__panel--list">
      <div className="panel__header">
        <div className="panel__header-main">
          <div>
            <p className="panel__eyebrow">{isSearching ? 'Search Results' : 'Word Shelf'}</p>
            <h2 className="panel__title">{isSearching ? '搜索结果' : selectedDictionary?.name || '单词列表'}</h2>
          </div>
          {activeWordCount > 0 && <span className="panel__count">{activeWordCount} 个词条</span>}
        </div>

        <div className="panel__header-actions">
          {isCompact && selectedWord && (
            <button className="exam-history-btn" onClick={() => setShowMobileDetail(true)}>
              查看详情
            </button>
          )}
          {selectedDictionary && (
            <>
              <button className="exam-history-btn" onClick={handleOpenExamHistory}>
                考试历史
              </button>
              {canCreateExam && (
                <button className="exam-trigger-btn" onClick={handleOpenExamSetup}>
                  开始考试
                </button>
              )}
            </>
          )}
        </div>
      </div>

      <div className="panel__list-wrapper">
        <WordList
          words={metaWords}
          selectedWord={selectedWord}
          onSelectWord={handleSelectWord}
          loading={loading && metaWords.length === 0}
        />
      </div>

      {totalWordPages > 1 && (
        <div className="content__pagination">
          <button
            className="content__page-btn"
            disabled={wordPage === 1 || loading}
            onClick={() => setWordPage((prev) => prev - 1)}
          >
            ‹
          </button>
          <span className="content__page-info">{wordPage} / {totalWordPages}</span>
          <button
            className="content__page-btn"
            disabled={wordPage === totalWordPages || loading}
            onClick={() => setWordPage((prev) => prev + 1)}
          >
            ›
          </button>
        </div>
      )}
    </div>
  );

  const detailPanel = (
    <div className="content__panel content__panel--detail">
      <div className="panel__header">
        <div>
          <p className="panel__eyebrow">Word Detail</p>
          <h2 className="panel__title">单词详情</h2>
        </div>
        <span className="panel__count">{selectedWord ? '已选词条' : '等待选择'}</span>
      </div>
      <WordDetail word={selectedWord} />
    </div>
  );

  return (
    <div className="app">
      <header className="app__header">
        <div className="app__header-content">
          <div className="app__masthead">
            <div>
              <p className="app__eyebrow">Word Atelier</p>
              <h1 className="app__title">Roles, dictionaries, and exams now meet inside one learning workspace.</h1>
            </div>
            <div className="app__masthead-meta">
              <span className="app__masthead-chip">{workspaceLabel}</span>
              <span className="app__masthead-chip">{currentUser.displayName} · {currentUser.role}</span>
              <button className="app__logout" onClick={handleSignOut}>
                退出登录
              </button>
            </div>
          </div>

          <div className="app__hero">
            <div className="app__hero-copy">
              <p className="app__eyebrow">Workspace</p>
              <p className="app__subtitle">
                管理员统筹资源，教师分配学习内容，学生只处理自己的词书与考试。所有请求都带着登录态进入后端。
              </p>
              <div className="app__hero-stats">
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">当前角色</span>
                  <strong className="app__hero-stat-value">{currentUser.role}</strong>
                </div>
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">辞书数量</span>
                  <strong className="app__hero-stat-value">{dictionaries.length}</strong>
                </div>
                <div className="app__hero-stat">
                  <span className="app__hero-stat-label">工作区状态</span>
                  <strong className="app__hero-stat-value">{workspaceMeta}</strong>
                </div>
              </div>
              {importFeedback && (
                <div className={`sidebar__import-feedback sidebar__import-feedback--${importFeedback.type}`}>
                  {importFeedback.message}
                </div>
              )}
            </div>

            <div className="app__hero-search">
              <div className="app__search-card">
                <div className="app__search-card-header">
                  <p className="app__search-label">快速搜索</p>
                  <span className="app__search-mode">{workspaceMeta}</span>
                </div>
                <div className="app__search">
                  <SearchBox
                    onLoading={setLoading}
                    onClear={handleSearchClear}
                    onSearchQueryChange={handleSearchQueryChange}
                    value={searchKeyword}
                  />
                </div>
                <p className="app__search-help">
                  搜索会切到词条工作区。移动端使用抽屉查看详情，桌面端保持双栏阅读。
                </p>
              </div>
            </div>
          </div>

          {isCompact && (
            <div className="app__mobile-switcher">
              <button
                className={`app__mobile-switch ${mobilePanel === 'library' ? 'app__mobile-switch--active' : ''}`}
                onClick={() => setMobilePanel('library')}
              >
                辞书
              </button>
              <button
                className={`app__mobile-switch ${mobilePanel === 'words' ? 'app__mobile-switch--active' : ''}`}
                onClick={() => setMobilePanel('words')}
              >
                词条
              </button>
              <button
                className={`app__mobile-switch ${showMobileDetail ? 'app__mobile-switch--active' : ''}`}
                onClick={() => selectedWord && setShowMobileDetail(true)}
                disabled={!selectedWord}
              >
                详情
              </button>
            </div>
          )}
        </div>
      </header>

      {!isCompact ? (
        <main className={`app__main ${sidebarCollapsed ? 'app__main--sidebar-collapsed' : ''}`}>
          <aside className={`app__sidebar ${sidebarCollapsed ? 'app__sidebar--collapsed' : ''}`}>
            <button
              className="sidebar__toggle"
              onClick={() => setSidebarCollapsed((prev) => !prev)}
              title={sidebarCollapsed ? '展开辞书列表' : '收起辞书列表'}
            >
              {sidebarCollapsed ? '▶' : '◀'}
            </button>
            {!sidebarCollapsed && sidebarContent}
          </aside>

          <section className="app__workspace">
            {listPanel}
            {detailPanel}
          </section>
        </main>
      ) : (
        <main className="app__main app__main--compact">
          {mobilePanel === 'library' ? (
            <section className="app__mobile-stage app__mobile-stage--library">{sidebarContent}</section>
          ) : (
            <section className="app__mobile-stage app__mobile-stage--words">{listPanel}</section>
          )}
        </main>
      )}

      {isCompact && selectedWord && (
        <div className={`detail-drawer ${showMobileDetail ? 'detail-drawer--open' : ''}`}>
          <button
            className={`detail-drawer__scrim ${showMobileDetail ? 'detail-drawer__scrim--open' : ''}`}
            aria-label="关闭单词详情"
            onClick={() => setShowMobileDetail(false)}
          />
          <aside className="detail-drawer__panel" aria-modal="true" role="dialog">
            <div className="detail-drawer__header">
              <div>
                <p className="panel__eyebrow">Focused Entry</p>
                <h2 className="panel__title">{selectedWord.word}</h2>
              </div>
              <button className="modal__close" onClick={() => setShowMobileDetail(false)}>
                ×
              </button>
            </div>
            <div className="detail-drawer__body">
              <WordDetail word={selectedWord} />
            </div>
          </aside>
        </div>
      )}

      <CreateDictionaryModal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onDictionaryCreated={handleDictionaryCreated}
      />

      {dictionaryForAdd && (
        <AddWordListModal
          isOpen={showAddWordListModal}
          onClose={() => {
            setShowAddWordListModal(false);
            setDictionaryForAdd(null);
            loadDictionaries();
          }}
          dictionary={dictionaryForAdd}
          onSuccess={() => {
            loadDictionaries();
            setWordPage(1);
          }}
        />
      )}

      {dictionaryForCsvImport && (
        <CsvImportModal
          isOpen={showCsvImportModal}
          onClose={() => {
            setShowCsvImportModal(false);
            setDictionaryForCsvImport(null);
            loadDictionaries();
          }}
          dictionary={dictionaryForCsvImport}
          onSuccess={() => {
            loadDictionaries();
            setWordPage(1);
          }}
        />
      )}

      <CreateExamModal
        isOpen={showExamSetupModal}
        dictionary={selectedDictionary}
        availableStudents={availableStudents}
        loading={examLoading}
        error={examError}
        onClose={() => {
          setShowExamSetupModal(false);
          setExamError(null);
        }}
        onStart={handleStartExam}
      />

      <ExamHistoryModal
        isOpen={showExamHistoryModal}
        dictionary={selectedDictionary}
        historyItems={examHistory}
        loading={examLoading}
        error={examError}
        onClose={() => {
          setShowExamHistoryModal(false);
          setExamError(null);
        }}
        onViewResult={handleViewHistoryResult}
      />

      <ExamSessionModal
        isOpen={activeExam !== null}
        dictionary={selectedDictionary}
        exam={activeExam}
        selectedAnswers={examAnswers}
        result={examResult}
        loading={examLoading}
        error={examError}
        onClose={handleCloseExam}
        onSelectOption={handleSelectExamOption}
        onSubmit={handleSubmitExam}
      />
    </div>
  );
}

export default App;
