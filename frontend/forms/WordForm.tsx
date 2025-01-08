import {addLink, addToCompound, addWord, createCompound, fetchBackend, updateWord} from "@/api";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import LanguageSelect from "@/components/LanguageSelect";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import FormCheckbox from "@/components/FormCheckbox";
import {useState} from "react";
import RuleListSelect from "@/components/RuleListSelect";
import {useRouter} from "next/router";
import PosSelect from "@/components/PosSelect";
import WordClassSelect from "@/components/WordClassSelect";
import {AddWordParameters, WordViewModel} from "@/models";

export interface WordFormData extends AddWordParameters {
    language?: string
    contextGloss?: string
    linkRuleNames?: string
    markHead?: boolean
    linkSource?: string
    linkNotes?: string
}

interface WordFormProps extends EtymographFormProps<WordFormData, WordViewModel> {
    linkType?: string
    languageReadOnly?: boolean
    linkTarget?: WordViewModel
    reverseLink?: boolean
    newCompound?: boolean
    addToCompound?: number
    linkTargetText?: string
    textReadOnly?: boolean
    showContextGloss?: boolean
    hideReconstructed?: boolean
    wordSubmitted?: (word: WordViewModel, baseWord: WordViewModel | undefined, formData: WordFormData) => any
}

export default function WordForm(props: WordFormProps) {
    const router = useRouter()
    const graph = router.query.graph as string

    const isAddingLink = props.linkType !== undefined

    const [isNewWord, setNewWord] = useState(false)
    const [wordDefinitions, setWordDefinitions] = useState([])
    const [forceNew, setForceNew] = useState(false)

    async function submitted(wordJson: WordViewModel, data: WordFormData) {
        if (isAddingLink) {
            let linkTarget = props.linkTarget
            if (props.linkTargetText !== undefined) {
                if (wordJson.text.toLocaleLowerCase('fr') === props.linkTargetText.toLocaleLowerCase('fr')) {
                    if (props.wordSubmitted !== undefined) {
                        props.wordSubmitted(wordJson, undefined, data)
                    }
                    return
                }
                else {
                    const addWordResponse = await addWord(graph, data.language, props.linkTargetText)
                    linkTarget = await addWordResponse.json()
                }
            }

            let fromId, toId;
            if (props.reverseLink === true) {
                [fromId, toId] = [linkTarget.id, wordJson.id]
            } else {
                [fromId, toId] = [wordJson.id, linkTarget.id]
            }
            const r = await addLink(graph, fromId, toId, props.linkType, data.linkRuleNames, data.linkSource, data.linkNotes)
            if (r.status !== 200) {
                const jr = await r.json()
                return {message: jr.message}
            }
            if (props.linkTargetText !== undefined && props.wordSubmitted !== undefined) {
                props.wordSubmitted(linkTarget, wordJson, data)
                return
            }
        }
        else if (props.newCompound === true) {
            await createCompound(graph, props.linkTarget.id, wordJson.id, data.linkSource, data.linkNotes)
        }
        else if (props.addToCompound !== undefined) {
            await addToCompound(graph, props.addToCompound, wordJson.id, data.markHead)
        }
        if (props.wordSubmitted !== undefined) {
            props.wordSubmitted(wordJson, undefined, data)
        }
    }

    async function updateWordStatus(data) {
        const wordResponse = await fetchBackend(graph, `word/${data.language}/${data.text}`)
        if (wordResponse.notFound !== undefined) {
            setNewWord(true)
            setWordDefinitions([])
        }
        else {
            setNewWord(false)
            const wordJson = wordResponse.props.loaderData
            if (Array.isArray(wordJson)) {
                setWordDefinitions(wordJson.map(w => w.gloss))
            }
            else {
                setWordDefinitions([wordJson.gloss])
            }
        }
    }

    return <EtymographForm<WordFormData, WordViewModel>
        create={(data) => addWord(graph, data.language, data.text, data.gloss, data.fullGloss, data.pos, data.classes,
            data.reconstructed, data.source, data.notes, forceNew)}
        update={(data) => updateWord(graph, props.updateId, data.text, data.gloss, data.fullGloss, data.pos, data.classes,
            data.reconstructed, data.source, data.notes)}
        {...props}
        submitted={submitted}
    >

        <table><tbody>
        {props.languageReadOnly !== true && <LanguageSelect id="language" label="Language"/>}
        <FormRow id="text" label="Text" readOnly={props.textReadOnly === true} inputAssist={true}
                 handleBlur={updateWordStatus}>
            {isNewWord && <span className="newWord">New</span>}
            {wordDefinitions.length > 0 && <>
                <span className="wordDefinitions">{wordDefinitions.join(", ")}</span>
                {' '}
                <button type="button" className="inlineButton inlineButtonLink" onClick={() => setForceNew(!forceNew)}>
                    {forceNew ? <b>New</b> : "New"}
                </button>
            </>}
        </FormRow>
        <PosSelect id="pos" label="POS" language={props.defaultValues.language} languageProp={props.languageReadOnly !== true ? 'language' : undefined}/>
        <WordClassSelect
            id="classes" label="Classes"
            language={props.defaultValues.language} languageProp={props.languageReadOnly !== true ? 'language' : undefined}
            posProp="pos"
        />
        <FormRow id="gloss" label="Gloss"/>
        <FormRow id="fullGloss" label="Full gloss"/>
        {props.showContextGloss && <FormRow id="contextGloss" label="Context gloss"/>}
        {(props.linkType === '>' || props.linkType === '^') &&
            <RuleListSelect id="linkRuleNames" label="Link rule names" isMulti={true}
                            language={props.defaultValues.language} languageProp={props.languageReadOnly !== true ? 'language' : undefined}/>}
        <FormRow id="source" label="Source"/>
        <tr>
            <td>Notes:</td>
            <td><FormTextArea rows={3} cols={50} id="notes"/></td>
        </tr>
        {(props.newCompound === true || isAddingLink) && <>
            <FormRow id="linkSource" label={isAddingLink ? "Link source" : "Compound source"}/>
            <FormRow id="linkNotes" label={isAddingLink ? "Link notes" : "Compound notes"}/>
        </>}
        </tbody></table>
        {!props.hideReconstructed && <FormCheckbox id="reconstructed" label="Reconstructed"/>}
        {props.addToCompound !== undefined && <FormCheckbox id="markHead" label="Mark as head"/>}
    </EtymographForm>
}
