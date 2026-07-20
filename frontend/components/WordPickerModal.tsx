import {KeyboardEvent as ReactKeyboardEvent, useContext, useEffect, useRef, useState} from "react";
import {
    Button,
    Checkbox,
    Modal,
    MultiSelect,
    ScrollArea,
    Select,
    Stack,
    Tabs,
    Textarea,
    TextInput
} from "@mantine/core";
import {useRouter} from "next/router";
import {addLink, addToCompound, createCompound, fetchBackend} from "@/api";
import {GlobalStateContext} from "@/components/Contexts";
import {LinkTypes} from "@/components/LinkTypes";
import WordForm, {WordFormData} from "@/forms/WordForm";
import WordTextView from "@/components/WordTextView";
import {DictionaryViewModel, DictionaryWordViewModel, WordRefViewModel, WordViewModel} from "@/models";

interface WordPickerModalProps {
    opened: boolean
    onClose: () => void
    title: string
    linkTarget: WordViewModel
    linkType?: string
    reverseLink?: boolean
    addToCompound?: number
    newCompound?: boolean
    suggestions?: WordRefViewModel[]
    languageReadOnly?: boolean
    showSyllabographic?: boolean
    defaultTab?: string
    defaultValues: WordFormData
    wordSubmitted: (word: WordViewModel, baseWord: WordViewModel | undefined, formData: WordFormData) => any
}

function commonPrefixLength(a: string, b: string) {
    let i = 0
    while (i < a.length && i < b.length && a[i] === b[i]) i++
    return i
}

export default function WordPickerModal(props: WordPickerModalProps) {
    const router = useRouter()
    const graph = router.query.graph as string
    const globalState = useContext(GlobalStateContext)

    const isAddToCompound = props.addToCompound !== undefined
    const isNewCompound = props.newCompound === true
    const isLink = !isAddToCompound && !isNewCompound
    const showRuleNames = isLink && (props.linkType === LinkTypes.Derived || props.linkType === LinkTypes.Origin)
    const showSourceNotes = isLink || isNewCompound

    const [language, setLanguage] = useState(props.defaultValues.language)
    const [search, setSearch] = useState("")
    const [words, setWords] = useState([] as DictionaryWordViewModel[])
    const [selectedWordId, setSelectedWordId] = useState<number | null>(null)
    const [ruleNames, setRuleNames] = useState([] as string[])
    const [linkSource, setLinkSource] = useState("")
    const [linkNotes, setLinkNotes] = useState("")
    const [markHead, setMarkHead] = useState(false)
    const [errorText, setErrorText] = useState("")
    const [activeTab, setActiveTab] = useState<string | null>(props.defaultTab ?? "existing")
    const selectedItemRef = useRef<HTMLButtonElement | null>(null)
    const newWordPanelRef = useRef<HTMLDivElement | null>(null)
    const scrollViewportRef = useRef<HTMLDivElement | null>(null)

    useEffect(() => {
        if (props.opened) {
            setLanguage(props.defaultValues.language)
            setSearch("")
            setSelectedWordId(null)
            setRuleNames([])
            setLinkSource("")
            setLinkNotes("")
            setMarkHead(false)
            setErrorText("")
            setActiveTab(props.defaultTab ?? "existing")
        }
    }, [props.opened, props.defaultValues.language, props.defaultTab])

    useEffect(() => {
        if (!props.opened || !language) {
            setWords([])
            return
        }
        let cancelled = false
        fetchBackend(graph, `dictionary/${language}/all`).then(r => {
            if (cancelled) return
            const dict = r?.props?.loaderData as DictionaryViewModel | undefined
            const loadedWords = dict?.words ?? []
            setWords(loadedWords)
            if (!isAddToCompound && !isNewCompound) {
                setSelectedWordId(closestPrefixMatchId(loadedWords))
            }
        })
        return () => { cancelled = true }
    }, [props.opened, graph, language])

    useEffect(() => {
        if (props.opened && suggestions.length > 0) {
            scrollViewportRef.current?.scrollTo({top: 0})
        }
    }, [props.opened, words, props.suggestions])

    useEffect(() => {
        if (selectedWordId !== null) {
            selectedItemRef.current?.scrollIntoView({block: "nearest"})
        }
    }, [selectedWordId, words])

    function closestPrefixMatchId(candidates: DictionaryWordViewModel[]): number | null {
        const target = props.linkTarget.text.toLocaleLowerCase()
        let bestId: number | null = null
        let bestText = ""
        let bestLength = 0
        for (const w of candidates) {
            if (w.ref.id === props.linkTarget.id) continue
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
    const ruleOptions = globalState.rules
        .filter(r => !language || language === r.toLang)
        .map(r => ({
            value: r.name,
            label: r.summaryText === null || r.summaryText === "" || r.summaryText.length > 15
                ? r.name : `${r.name} (${r.summaryText})`
        }))

    const suggestions = props.suggestions ?? []
    const suggestionIds = new Set(suggestions.map(s => s.id))
    const searchLower = search.toLocaleLowerCase()
    const filteredWords = words.filter(w =>
        w.ref.id !== props.linkTarget.id &&
        !suggestionIds.has(w.ref.id) &&
        (searchLower === "" ||
            w.ref.text.toLocaleLowerCase().startsWith(searchLower))
    ).sort((a, b) => a.ref.text.localeCompare(b.ref.text))

    function wordItem(ref: WordRefViewModel) {
        return <button key={ref.id} type="button"
                       ref={selectedWordId === ref.id ? selectedItemRef : undefined}
                       className={selectedWordId === ref.id ? "wordPickerItem wordPickerItemSelected" : "wordPickerItem"}
                       onClick={() => setSelectedWordId(ref.id)}
                       onDoubleClick={() => { setSelectedWordId(ref.id); addExistingClicked(ref.id) }}>
            <WordTextView text={ref.text} syllabograms={ref.syllabogramSequence}/>
            {ref.gloss && <> &apos;{ref.gloss}&apos;</>}
        </button>
    }

    async function addExistingClicked(wordId: number | null = selectedWordId) {
        if (wordId === null) return
        let r: Response
        if (isAddToCompound) {
            r = await addToCompound(graph, props.addToCompound, wordId, markHead)
        }
        else if (isNewCompound) {
            r = await createCompound(graph, props.linkTarget.id, wordId, linkSource || null, linkNotes || null)
        }
        else {
            const [fromId, toId] = props.reverseLink === true
                ? [props.linkTarget.id, wordId]
                : [wordId, props.linkTarget.id]
            r = await addLink(graph, fromId, toId, props.linkType, ruleNames.join(","),
                linkSource || null, linkNotes || null)
        }
        if (r.status !== 200) {
            const jr = await r.json()
            setErrorText(jr.message)
            return
        }
        props.wordSubmitted(undefined, undefined, props.defaultValues)
    }

    function handleKeyDown(e: ReactKeyboardEvent) {
        if (e.key !== "Enter" || e.defaultPrevented) return
        const tag = (e.target as HTMLElement).tagName
        if (tag === "TEXTAREA" || tag === "BUTTON") return
        if (activeTab === "existing") {
            addExistingClicked()
        }
        else {
            e.preventDefault()
            newWordPanelRef.current?.querySelector("form")?.requestSubmit()
        }
    }

    return <Modal opened={props.opened} onClose={props.onClose} title={props.title} size="xl" onKeyDown={handleKeyDown}>
        <Tabs value={activeTab} onChange={setActiveTab}>
            <Tabs.List>
                <Tabs.Tab value="existing">Existing word</Tabs.Tab>
                <Tabs.Tab value="new">New word</Tabs.Tab>
            </Tabs.List>

            <Tabs.Panel value="existing" pt="sm">
                <Stack gap="sm">
                    {props.languageReadOnly !== true &&
                        <Select label="Language" data={languageOptions} value={language ?? null}
                                onChange={(val) => setLanguage(val ?? "")} searchable={true} allowDeselect={false}/>}
                    <TextInput label="Search" value={search} onChange={(e) => setSearch(e.currentTarget.value)}/>
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
                    {showRuleNames &&
                        <MultiSelect label="Link rule names" data={ruleOptions} value={ruleNames}
                                     onChange={setRuleNames} searchable={true} clearable={true}/>}
                    {isAddToCompound &&
                        <Checkbox label="Mark as head" checked={markHead}
                                  onChange={(e) => setMarkHead(e.currentTarget.checked)}/>}
                    {showSourceNotes && <>
                        <TextInput label={isLink ? "Link source" : "Compound source"} value={linkSource}
                                   onChange={(e) => setLinkSource(e.currentTarget.value)}/>
                        <Textarea label={isLink ? "Link notes" : "Compound notes"} rows={3} value={linkNotes}
                                  onChange={(e) => setLinkNotes(e.currentTarget.value)}/>
                    </>}
                    {errorText !== "" && <div className="errorText">{errorText}</div>}
                    <Button disabled={selectedWordId === null} onClick={() => addExistingClicked()} style={{alignSelf: 'flex-start'}}>
                        {isAddToCompound ? "Add component" : isNewCompound ? "Create compound" : "Add link"}
                    </Button>
                </Stack>
            </Tabs.Panel>

            <Tabs.Panel value="new" pt="sm" ref={newWordPanelRef}>
                <WordForm
                    linkType={props.linkType}
                    linkTarget={props.linkTarget}
                    reverseLink={props.reverseLink}
                    addToCompound={props.addToCompound}
                    newCompound={props.newCompound}
                    languageReadOnly={props.languageReadOnly}
                    showSyllabographic={props.showSyllabographic}
                    defaultValues={props.defaultValues}
                    wordSubmitted={props.wordSubmitted}
                    cancelled={props.onClose}
                />
            </Tabs.Panel>
        </Tabs>
    </Modal>
}
