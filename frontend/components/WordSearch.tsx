import {useContext, useEffect, useRef, useState} from "react";
import {ScrollArea, Select, Stack, TextInput} from "@mantine/core";
import {useRouter} from "next/router";
import {fetchBackend} from "@/api";
import {GlobalStateContext} from "@/components/Contexts";
import WordTextView from "@/components/WordTextView";
import {DictionaryViewModel, DictionaryWordViewModel, WordRefViewModel} from "@/models";

interface WordSearchProps {
    language: string
    onLanguageChange: (language: string) => void
    selectedWordId: number | null
    onSelectWord: (wordId: number | null) => void
    onConfirmWord?: (wordId: number) => void
    enabled?: boolean
    excludeWordId?: number
    closestMatchText?: string
    suggestions?: WordRefViewModel[]
    languageReadOnly?: boolean
    showSyllabographic?: boolean
}

function commonPrefixLength(a: string, b: string) {
    let i = 0
    while (i < a.length && i < b.length && a[i] === b[i]) i++
    return i
}

export function normalizeSearchText(text: string) {
    return text.toLocaleLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "")
}

export default function WordSearch(props: WordSearchProps) {
    const router = useRouter()
    const graph = router.query.graph as string
    const globalState = useContext(GlobalStateContext)
    const enabled = props.enabled ?? true
    const [search, setSearch] = useState("")
    const [words, setWords] = useState([] as DictionaryWordViewModel[])
    const selectedItemRef = useRef<HTMLButtonElement | null>(null)
    const scrollViewportRef = useRef<HTMLDivElement | null>(null)

    useEffect(() => {
        if (enabled) setSearch("")
    }, [enabled])

    useEffect(() => {
        if (!enabled || !props.language) {
            setWords([])
            return
        }
        let cancelled = false
        fetchBackend(graph, `dictionary/${props.language}/all`).then(r => {
            if (cancelled) return
            const dict = r?.props?.loaderData as DictionaryViewModel | undefined
            const loadedWords = dict?.words ?? []
            setWords(loadedWords)
            if (props.closestMatchText !== undefined) {
                props.onSelectWord(closestPrefixMatchId(loadedWords))
            }
        })
        return () => { cancelled = true }
    }, [enabled, graph, props.language, props.closestMatchText, props.excludeWordId, props.onSelectWord])

    useEffect(() => {
        if (enabled && suggestions.length > 0) {
            scrollViewportRef.current?.scrollTo({top: 0})
        }
    }, [enabled, words, props.suggestions])

    useEffect(() => {
        if (props.selectedWordId !== null) {
            selectedItemRef.current?.scrollIntoView({block: "nearest"})
        }
    }, [props.selectedWordId, words])

    function closestPrefixMatchId(candidates: DictionaryWordViewModel[]): number | null {
        const target = props.closestMatchText.toLocaleLowerCase()
        let bestId: number | null = null
        let bestText = ""
        let bestLength = 0
        for (const w of candidates) {
            if (w.ref.id === props.excludeWordId) continue
            const text = w.ref.text.toLocaleLowerCase()
            const length = commonPrefixLength(target, text)
            if (length > bestLength || (length === bestLength && length > 0 && text.localeCompare(bestText) < 0)) {
                bestId = w.ref.id
                bestText = text
                bestLength = length
            }
        }
        return bestId
    }

    const languageOptions = globalState.languages.map(
        (l) => ({value: l.shortName, label: `${l.name} (${l.shortName})`})
    )
    const suggestions = props.suggestions ?? []
    const suggestionIds = new Set(suggestions.map(s => s.id))
    const searchLower = normalizeSearchText(search)
    const filteredWords = words.filter(w =>
        w.ref.id !== props.excludeWordId &&
        !suggestionIds.has(w.ref.id) &&
        (searchLower === "" ||
            normalizeSearchText(w.ref.text).startsWith(searchLower))
        && (props.showSyllabographic || !w.ref.syllabogramSequence)
    ).sort((a, b) => a.ref.text.localeCompare(b.ref.text))

    function wordItem(ref: WordRefViewModel) {
        return <button key={ref.id} type="button"
                       ref={props.selectedWordId === ref.id ? selectedItemRef : undefined}
                       className={props.selectedWordId === ref.id ? "wordPickerItem wordPickerItemSelected" : "wordPickerItem"}
                       onClick={() => props.onSelectWord(ref.id)}
                       onDoubleClick={() => { props.onSelectWord(ref.id); props.onConfirmWord?.(ref.id) }}>
            <WordTextView text={ref.text} syllabograms={ref.syllabogramSequence}/>
            {ref.gloss && <> &apos;{ref.gloss}&apos;</>}
        </button>
    }

    return <Stack gap="sm">
        {props.languageReadOnly !== true &&
            <Select label="Language" data={languageOptions} value={props.language ?? null}
                    onChange={(val) => props.onLanguageChange(val ?? "")} searchable={true} allowDeselect={false}/>}
        <TextInput placeholder="Search" aria-label="Search" value={search} onChange={(e) => setSearch(e.currentTarget.value)}/>
        <ScrollArea.Autosize mah={250} type="auto" viewportRef={scrollViewportRef}>
            <Stack gap={0}>
                {suggestions.length > 0 && <>
                    <div className="wordPickerLabel">Suggested</div>
                    {suggestions.map(s => wordItem(s))}
                    <div className="wordPickerLabel">All words</div>
                </>}
                {filteredWords.map(w => wordItem(w.ref))}
                {filteredWords.length === 0 && suggestions.length === 0 &&
                    <div className="wordPickerLabel">No matching words</div>}
            </Stack>
        </ScrollArea.Autosize>
    </Stack>
}
