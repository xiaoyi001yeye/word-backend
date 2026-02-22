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

export interface Dictionary {
  id: number;
  name: string;
  filePath?: string;
  fileSize?: number;
  category?: string;
  wordCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface DictionaryWord {
  id: number;
  dictionaryId: number;
  metaWordId: number;
  createdAt?: string;
}
