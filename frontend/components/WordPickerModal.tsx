import {KeyboardEvent as ReactKeyboardEvent, useContext, useEffect, useRef, useState} from "react";
import {
    Button,
    Checkbox,
    Input,
    Modal,
    Stack,
    Tabs,
    Textarea,
    TextInput
} from "@mantine/core";
import {useRouter} from "next/router";
import ReactSelect from "react-select";
import {addLink, addToCompound, createCompound, replaceCompoundMember} from "@/api";
import {GlobalStateContext} from "@/components/Contexts";
import {LinkTypes} from "@/components/LinkTypes";
import WordForm, {WordFormData} from "@/forms/WordForm";
import WordSearch from "@/components/WordSearch";
import {WordRefViewModel, WordViewModel} from "@/models";

interface WordPickerModalProps {
    opened: boolean
    onClose: () => void
    title: string
    linkTarget: WordViewModel
    linkType?: string
    reverseLink?: boolean
    addToCompound?: number
    replaceCompoundMember?: {compoundId: number, memberIndex: number}
    newCompound?: boolean
    suggestions?: WordRefViewModel[]
    languageReadOnly?: boolean
    showSyllabographic?: boolean
    defaultTab?: string
    defaultValues: WordFormData
    wordSubmitted: (word: WordViewModel, baseWord: WordViewModel | undefined, formData: WordFormData) => any
}

export default function WordPickerModal(props: WordPickerModalProps) {
    const router = useRouter()
    const graph = router.query.graph as string
    const globalState = useContext(GlobalStateContext)

    const isAddToCompound = props.addToCompound !== undefined
    const isReplacingCompoundMember = props.replaceCompoundMember !== undefined
    const isNewCompound = props.newCompound === true
    const isLink = !isAddToCompound && !isReplacingCompoundMember && !isNewCompound
    const showRuleNames = isLink && (props.linkType === LinkTypes.Derived || props.linkType === LinkTypes.Origin)
    const showSourceNotes = isLink || isNewCompound

    const [language, setLanguage] = useState(props.defaultValues.language)
    const [selectedWordId, setSelectedWordId] = useState<number | null>(null)
    const [ruleNames, setRuleNames] = useState([] as string[])
    const [linkSource, setLinkSource] = useState("")
    const [linkNotes, setLinkNotes] = useState("")
    const [markHead, setMarkHead] = useState(false)
    const [errorText, setErrorText] = useState("")
    const [activeTab, setActiveTab] = useState<string | null>(props.defaultTab ?? "existing")
    const newWordPanelRef = useRef<HTMLDivElement | null>(null)

    useEffect(() => {
        if (props.opened) {
            setLanguage(props.defaultValues.language)
            setSelectedWordId(null)
            setRuleNames([])
            setLinkSource("")
            setLinkNotes("")
            setMarkHead(false)
            setErrorText("")
            setActiveTab(props.defaultTab ?? "existing")
        }
    }, [props.opened, props.defaultValues.language, props.defaultTab])

    const ruleOptions = globalState.rules
        .filter(r => !language || language === r.toLang)
        .map(r => ({
            value: r.name,
            label: r.summaryText === null || r.summaryText === "" || r.summaryText.length > 15
                ? r.name : `${r.name} (${r.summaryText})`
        }))

    async function addExistingClicked(wordId: number | null = selectedWordId) {
        if (wordId === null) return
        let r: Response
        if (isAddToCompound) {
            r = await addToCompound(graph, props.addToCompound, wordId, markHead)
        }
        else if (isReplacingCompoundMember) {
            r = await replaceCompoundMember(graph, props.replaceCompoundMember.compoundId,
                props.replaceCompoundMember.memberIndex, wordId)
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
                    <WordSearch
                        key={props.defaultValues.language}
                        enabled={props.opened}
                        defaultSearch={props.defaultValues.text}
                        language={language}
                        onLanguageChange={setLanguage}
                        selectedWordId={selectedWordId}
                        onSelectWord={setSelectedWordId}
                        onConfirmWord={addExistingClicked}
                        excludeWordId={props.linkTarget.id}
                        closestMatchText={isLink ? props.linkTarget.text : undefined}
                        suggestions={props.suggestions}
                        languageReadOnly={props.languageReadOnly}
                        showSyllabographic={props.showSyllabographic}
                    />
                    {showRuleNames &&
                        <div>
                            <Input.Label htmlFor="word-picker-rule-names">Link rule names</Input.Label>
                            <ReactSelect inputId="word-picker-rule-names"
                                         options={ruleOptions}
                                         isMulti={true}
                                         value={ruleNames.map(name => ruleOptions.find(option => option.value === name))
                                             .filter(option => option !== undefined)}
                                         onChange={(selected) => setRuleNames(selected.map(option => option.value))}
                                         isSearchable={true}
                                         isClearable={true}/>
                        </div>}
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
                        {isAddToCompound ? "Add component" : isReplacingCompoundMember ? "Change component" : isNewCompound ? "Create compound" : "Add link"}
                    </Button>
                </Stack>
            </Tabs.Panel>

            <Tabs.Panel value="new" pt="sm" ref={newWordPanelRef}>
                <WordForm
                    linkType={props.linkType}
                    linkTarget={props.linkTarget}
                    reverseLink={props.reverseLink}
                    addToCompound={props.addToCompound}
                    replaceCompoundMember={props.replaceCompoundMember}
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
