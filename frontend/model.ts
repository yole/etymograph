 interface GraphViewModel {
    id: string;
    name: string;
}

export interface WordCategoryValueViewModel {
    name: string;
    abbreviation: string;
}

export interface WordCategoryViewModel {
    name: string;
    pos: string[];
    values: WordCategoryValueViewModel[];
}

export interface LanguageShortViewModel {
    name: string;
    shortName: string;
    pos: WordCategoryValueViewModel[];
    wordClasses: WordCategoryViewModel[];
    dictionaries: string[];
}

export interface PublicationData {
    id?: number;
    refId: string;
    name: string;
}

export interface RichTextFragmentModel {
    text: string;
    tooltip?: string;
    emph?: boolean;
    linkType?: string;
    linkId?: number;
    linkLanguage?: string;
    linkData?: string;
}

export interface RichTextModel {
    fragments: RichTextFragmentModel[];
}

export interface RuleRefViewModel {
    id: number;
    name: string;
}

export interface RuleShortViewModel {
    id: number;
    name: string;
    toLang: string;
    summaryText: string;
    optional: boolean;
}

export interface SourceRefViewModel {
    pubId?: number;
    pubRefId?: string;
    refText: string;
}

export interface WordRefViewModel {
    id: number;
    text: string;
    language: string;
    displayLanguage: string;
    gloss?: string;
    homonym: boolean;
    reconstructed: boolean;
}
