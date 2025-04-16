// This file is auto-generated by @hey-api/openapi-ts

export type AcceptAlternativeParameters = {
    index: number;
    wordId: number;
    ruleId: number;
};

export type AddPublicationParameters = {
    name?: string;
    refId?: string;
    author?: string;
    date?: string;
    publisher?: string;
};

export type AddWordParameters = {
    text?: string;
    gloss?: string;
    fullGloss?: string;
    pos?: string;
    classes?: string;
    reconstructed?: boolean;
    source?: string;
    notes?: string;
    forceNew?: boolean;
};

export type AlternativeViewModel = {
    gloss: string;
    wordId: number;
    ruleId: number;
};

export type ApplySequenceParams = {
    linkFromId: number;
    linkToId: number;
};

export type AssociateWordParameters = {
    index: number;
    wordId: number;
    contextGloss?: string;
};

export type AttestationViewModel = {
    textId: number;
    textTitle: string;
    word?: string;
};

export type ComparePhonemesParameters = {
    toPhoneme: string;
};

export type ComparePhonemesResult = {
    message: string;
};

export type CompoundComponentsViewModel = {
    compoundId: number;
    components: Array<WordRefViewModel>;
    headIndex?: number;
    derivation: boolean;
    source: Array<SourceRefViewModel>;
    sourceEditableText: string;
    notes?: string;
};

export type CompoundParams = {
    compoundId: number;
    firstComponentId: number;
    head?: number;
    source?: string;
    notes?: string;
};

export type CopyPhonemesParams = {
    fromLang: string;
};

export type CorpusLangTextViewModel = {
    id: number;
    title: string;
};

export type CorpusLangViewModel = {
    language: Language;
    corpusTexts: Array<CorpusLangTextViewModel>;
};

export type CorpusLineViewModel = {
    words: Array<CorpusWordViewModel>;
};

export type CorpusTextParams = {
    title: string;
    text: string;
    source: string;
    notes: string;
};

export type CorpusTextViewModel = {
    id: number;
    title: string;
    language: string;
    languageFullName: string;
    text: string;
    lines: Array<CorpusLineViewModel>;
    source: Array<SourceRefViewModel>;
    sourceEditableText: string;
    notes?: string;
    translations: Array<TranslationViewModel>;
};

export type CorpusWordCandidateViewModel = {
    id: number;
    gloss?: string;
};

export type CorpusWordViewModel = {
    index: number;
    text: string;
    normalizedText: string;
    gloss: string;
    contextGloss?: string;
    wordId?: number;
    wordText?: string;
    wordCandidates?: Array<CorpusWordCandidateViewModel>;
    stressIndex?: number;
    stressLength?: number;
    homonym: boolean;
};

export type DerivationViewModel = {
    baseWord: WordRefViewModel;
    derivation: LinkWordViewModel;
    expectedWord?: string;
    singlePhonemeDifference?: string;
    pos?: string;
};

export type DeriveThroughSequenceParams = {
    sequenceId: number;
};

export type DictionaryViewModel = {
    language: Language;
    words: Array<DictionaryWordViewModel>;
};

export type DictionaryWordViewModel = {
    id: number;
    text: string;
    gloss: string;
    fullGloss?: string;
    homonym: boolean;
};

export type GenerateParadigmParameters = {
    name: string;
    lang: string;
    pos: string;
    addedCategories: string;
    prefix: string;
    rows: string;
    columns: string;
};

export type GraphViewModel = {
    id: string;
    name: string;
};

export type InputAssistGraphemeViewModel = {
    text: string;
    languages: Array<(string)>;
};

export type InputAssistViewModel = {
    graphemes: Array<InputAssistGraphemeViewModel>;
};

export type Language = {
    name: string;
    shortName: string;
    reconstructed: boolean;
    phonemes?: Array<Phoneme>;
    diphthongs: Array<(string)>;
    syllableStructures: Array<(string)>;
    stressRule?: RuleRef;
    phonotacticsRule?: RuleRef;
    pronunciationRule?: RuleRef;
    orthographyRule?: RuleRef;
    pos?: Array<WordCategoryValue>;
    grammaticalCategories?: Array<WordCategory>;
    wordClasses?: Array<WordCategory>;
    orthoPhonemeLookup?: PhonemeLookup;
    phonoPhonemeLookup?: PhonemeLookup;
    dictionarySettings?: string;
};

export type LanguageShortViewModel = {
    name: string;
    shortName: string;
    pos: Array<WordCategoryValueViewModel>;
    wordClasses: Array<WordCategoryViewModel>;
    dictionaries: Array<(string)>;
};

export type LanguageViewModel = {
    name: string;
    shortName: string;
    reconstructed: boolean;
    diphthongs: Array<(string)>;
    phonemes: Array<PhonemeTableViewModel>;
    stressRuleId?: number;
    stressRuleName?: string;
    phonotacticsRuleId?: number;
    phonotacticsRuleName?: string;
    pronunciationRuleId?: number;
    pronunciationRuleName?: string;
    orthographyRule?: RuleRefViewModel;
    syllableStructures: Array<(string)>;
    pos: string;
    grammaticalCategories: string;
    wordClasses: string;
    dictionarySettings?: string;
};

export type LinkedRuleViewModel = {
    ruleId: number;
    ruleName: string;
    linkType: string;
    source: Array<SourceRefViewModel>;
    notes?: string;
};

export type LinkParams = {
    fromEntity: number;
    toEntity: number;
    linkType: string;
    ruleNames: string;
    source: string;
    notes?: string;
};

export type LinkTypeViewModel = {
    typeId: string;
    type: string;
    words: Array<LinkWordViewModel>;
};

export type LinkWordViewModel = {
    word: WordRefViewModel;
    ruleIds: Array<(number)>;
    ruleNames: Array<(string)>;
    ruleResults: Array<(string)>;
    ruleSequence?: WordRuleSequenceViewModel;
    source: Array<SourceRefViewModel>;
    sourceEditableText: string;
    notes?: string;
    suggestedSequences: Array<WordRuleSequenceViewModel>;
};

export type LookupParameters = {
    dictionaryId: string;
    disambiguation?: string;
};

export type LookupResultViewModel = {
    status?: string;
    variants: Array<LookupVariantViewModel>;
};

export type LookupVariantViewModel = {
    text: string;
    disambiguation?: string;
};

export type ParadigmCellViewModel = {
    alternativeRuleNames: Array<(string)>;
    alternativeRuleSummaries: Array<(string)>;
    alternativeRuleIds: Array<(number)>;
};

export type ParadigmColumnViewModel = {
    title: string;
    cells: Array<ParadigmCellViewModel>;
};

export type ParadigmRefViewModel = {
    id: number;
    name: string;
    refType: string;
};

export type ParadigmViewModel = {
    id: number;
    name: string;
    language: string;
    languageFullName: string;
    pos: Array<(string)>;
    rowTitles: Array<(string)>;
    columns: Array<ParadigmColumnViewModel>;
    editableText: string;
    preRule?: RuleRefViewModel;
    postRule?: RuleRefViewModel;
};

export type ParseCandidatesViewModel = {
    parseCandidates: Array<ParseCandidateViewModel>;
};

export type ParseCandidateViewModel = {
    text: string;
    categories: string;
    ruleNames: Array<(string)>;
    pos?: string;
    wordId?: number;
};

export type Phoneme = {
    graphemes: Array<(string)>;
    sound?: string;
    classes: Array<(string)>;
    historical: boolean;
    id: number;
    source: Array<SourceRef>;
    notes?: string;
};

export type PhonemeLookup = {
    [key: string]: unknown;
};

export type PhonemeRuleGroupViewModel = {
    title: string;
    rules: Array<PhonemeRuleViewModel>;
};

export type PhonemeRuleViewModel = {
    id: number;
    name: string;
    summary: string;
};

export type PhonemeTableCellViewModel = {
    phonemes: Array<PhonemeViewModel>;
};

export type PhonemeTableRowViewModel = {
    title: string;
    cells: Array<PhonemeTableCellViewModel>;
};

export type PhonemeTableViewModel = {
    title: string;
    columnTitles: Array<(string)>;
    rows: Array<PhonemeTableRowViewModel>;
};

export type PhonemeViewModel = {
    id: number;
    languageShortName: string;
    languageFullName: string;
    graphemes: Array<(string)>;
    sound: string;
    classes: string;
    implicitClasses: string;
    features: string;
    historical: boolean;
    source: Array<SourceRefViewModel>;
    sourceEditableText: string;
    notes?: string;
    relatedRules: Array<PhonemeRuleGroupViewModel>;
};

export type PublicationViewModel = {
    id: number;
    name: string;
    author?: string;
    date?: string;
    publisher?: string;
    refId: string;
};

export type ReapplyResultViewModel = {
    consistent: number;
    becomesConsistent: number;
    becomesInconsistent: number;
    inconsistent: number;
};

export type RichText = {
    fragments: Array<RichTextFragment>;
};

export type RichTextFragment = {
    text: string;
    tooltip?: string;
    emph: boolean;
    subscript: boolean;
    linkType?: string;
    linkId?: number;
    linkLanguage?: string;
    linkData?: string;
};

export type RuleBranchViewModel = {
    conditions: RichText;
    instructions: Array<RichText>;
    comment?: string;
    examples: Array<RuleExampleViewModel>;
};

export type RuleExampleViewModel = {
    fromWord: WordRefViewModel;
    toWord: WordRefViewModel;
    expectedWord?: (string) | null;
    wordBeforeRule?: (string) | null;
    wordAfterRule?: (string) | null;
};

export type RuleGroupViewModel = {
    groupName: string;
    rules: Array<RuleShortViewModel>;
    sequenceId?: number;
    sequenceName?: string;
    sequenceFromLang?: string;
    sequenceToLang?: string;
    paradigmId?: number;
};

export type RuleLinkParams = {
    fromEntity: number;
    toRuleName: string;
    linkType: string;
    source: string;
    notes?: string;
};

export type RuleLinkViewModel = {
    toRuleId: number;
    toRuleName: string;
    linkType: string;
    source: Array<SourceRefViewModel>;
    notes?: string;
};

export type RuleListViewModel = {
    toLangFullName: string;
    ruleGroups: Array<RuleGroupViewModel>;
};

export type RulePreviewParams = {
    newText: string;
};

export type RulePreviewResultListViewModel = {
    results: Array<RulePreviewResultViewModel>;
};

export type RulePreviewResultViewModel = {
    word: WordRefViewModel;
    oldForm: string;
    newForm: string;
};

export type RuleRef = {
    [key: string]: unknown;
};

export type RuleRefViewModel = {
    id: number;
    name: string;
};

export type RuleSequenceLinkViewModel = {
    sequenceName: string;
    prev?: RuleRefViewModel;
    next?: RuleRefViewModel;
};

export type RuleSequenceViewModel = {
    id: number;
    name: string;
    fromLang: string;
    toLang: string;
};

export type RuleShortViewModel = {
    id: number;
    name: string;
    toLang: string;
    summaryText: string;
    optional: boolean;
    dispreferred: boolean;
};

export type RuleTraceParams = {
    word: string;
    language?: string;
    reverse: boolean;
};

export type RuleTraceResult = {
    trace: string;
    result: string;
};

export type RuleViewModel = {
    id: number;
    name: string;
    fromLang: string;
    toLang: string;
    fromLangFullName: string;
    toLangFullName: string;
    summaryText: string;
    editableText: string;
    addedCategories?: string;
    replacedCategories?: string;
    addedCategoryDisplayNames?: string;
    fromPOS: Array<(string)>;
    toPOS?: string;
    source: Array<SourceRefViewModel>;
    sourceEditableText: string;
    notes?: string;
    paradigmId?: number;
    paradigmName?: string;
    paradigmPreRule?: RuleRefViewModel;
    paradigmPostRule?: RuleRefViewModel;
    phonemic: boolean;
    preInstructions: Array<RichText>;
    branches: Array<RuleBranchViewModel>;
    postInstructions: Array<RichText>;
    links: Array<RuleLinkViewModel>;
    references: Array<RuleRefViewModel>;
    sequenceLinks: Array<RuleSequenceLinkViewModel>;
    linkedWords: Array<RuleWordLinkViewModel>;
    orphanExamples: Array<RuleExampleViewModel>;
    referencingParadigms: Array<ParadigmRefViewModel>;
};

export type RuleWordLinkViewModel = {
    toWord: WordRefViewModel;
    linkType: string;
    source: Array<SourceRefViewModel>;
    notes?: string;
};

export type SequenceDerivationsViewModel = {
    sequence: RuleSequenceViewModel;
    derivations: Array<DerivationViewModel>;
};

export type SequenceReportViewModel = {
    name: string;
    toLang: string;
    rules: Array<SequenceRuleViewModel>;
};

export type SequenceRuleViewModel = {
    ruleName: string;
    ruleSource: string;
    ruleIsSPE: boolean;
    optional: boolean;
    preInstructions: Array<RichText>;
    branches: Array<RuleBranchViewModel>;
    postInstructions: Array<RichText>;
};

export type SourceRef = {
    pubId?: number;
    refText: string;
};

export type SourceRefViewModel = {
    pubId?: number;
    pubRefId?: string;
    refText: string;
};

export type SuggestCompoundParameters = {
    compoundId?: number;
};

export type SuggestCompoundViewModel = {
    suggestions: Array<WordRefViewModel>;
};

export type TranslationParams = {
    corpusTextId?: number;
    text: string;
    source: string;
};

export type TranslationViewModel = {
    id: number;
    text: string;
    source: Array<SourceRefViewModel>;
    sourceEditableText: string;
};

export type UpdateCompoundParams = {
    componentId: number;
    markHead: boolean;
};

export type UpdateLanguageParameters = {
    name?: string;
    shortName?: string;
    reconstructed?: boolean;
    phonemes?: string;
    diphthongs?: string;
    stressRuleName?: string;
    phonotacticsRuleName?: string;
    pronunciationRuleName?: string;
    orthographyRuleName?: string;
    syllableStructures?: string;
    pos?: string;
    grammaticalCategories?: string;
    wordClasses?: string;
    dictionarySettings?: string;
};

export type UpdateParadigmParameters = {
    name: string;
    pos: string;
    text: string;
    preRuleName?: string;
    postRuleName?: string;
};

export type UpdatePhonemeParameters = {
    graphemes: string;
    sound: string;
    classes: string;
    historical: boolean;
    source?: string;
    notes?: string;
};

export type UpdateRuleParameters = {
    name: string;
    fromLang: string;
    toLang: string;
    text: string;
    addedCategories?: string;
    replacedCategories?: string;
    fromPOS?: string;
    toPOS?: string;
    source?: string;
    notes?: string;
};

export type UpdateSequenceParams = {
    name: string;
    fromLang: string;
    toLang: string;
    ruleNames: string;
};

export type UpdateWordParadigmParameters = {
    items: Array<(unknown[])>;
};

export type WordCategory = {
    name: string;
    pos: Array<(string)>;
    values: Array<WordCategoryValue>;
};

export type WordCategoryValue = {
    name: string;
    abbreviation: string;
};

export type WordCategoryValueViewModel = {
    name: string;
    abbreviation: string;
};

export type WordCategoryViewModel = {
    name: string;
    pos: Array<(string)>;
    values: Array<WordCategoryValueViewModel>;
};

export type WordParadigmListModel = {
    word: string;
    wordId: number;
    language: string;
    languageFullName: string;
    paradigms: Array<WordParadigmModel>;
};

export type WordParadigmModel = {
    name: string;
    rowTitles: Array<(string)>;
    columnTitles: Array<(string)>;
    cells: Array<(unknown[])>;
};

export type WordRefViewModel = {
    id: number;
    text: string;
    language: string;
    displayLanguage: string;
    gloss?: string;
    homonym: boolean;
    reconstructed: boolean;
};

export type WordRuleSequenceViewModel = {
    name: string;
    id: number;
};

export type WordSequenceParams = {
    sequence: string;
    source: string;
};

export type WordSequenceResults = {
    words: Array<WordRefViewModel>;
    ruleIds: Array<(number)>;
};

export type WordViewModel = {
    id: number;
    language: string;
    languageFullName: string;
    languageReconstructed: boolean;
    text: string;
    textWithExplicitStress: string;
    gloss: string;
    glossComputed: boolean;
    fullGloss?: string;
    pos?: string;
    classes: Array<(string)>;
    reconstructed: boolean;
    source: Array<SourceRefViewModel>;
    sourceEditableText: string;
    notes?: string;
    attestations: Array<AttestationViewModel>;
    linksFrom: Array<LinkTypeViewModel>;
    linksTo: Array<LinkTypeViewModel>;
    compounds: Array<WordRefViewModel>;
    derivationalCompounds: Array<WordRefViewModel>;
    components: Array<CompoundComponentsViewModel>;
    linkedRules: Array<LinkedRuleViewModel>;
    stressIndex?: number;
    stressLength?: number;
    compound: boolean;
    hasParadigms: boolean;
    suggestedDeriveSequences: Array<WordRuleSequenceViewModel>;
};