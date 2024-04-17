import {addLink, addToCompound, addWord, createCompound, fetchBackend, updateWord} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import LanguageSelect from "@/components/LanguageSelect";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import FormCheckbox from "@/components/FormCheckbox";
import {useState} from "react";
import RuleListSelect from "@/components/RuleListSelect";
import {useRouter} from "next/router";

export default function WordForm(props) {
    const router = useRouter()
    const graph = router.query.graph

    const isAddingLink = props.linkType !== undefined

    const [isNewWord, setNewWord] = useState(false)
    const [wordDefinitions, setWordDefinitions] = useState([])

    async function submitted(wordJson, data) {
        if (isAddingLink) {
            let fromId, toId
            if (props.reverseLink === true) {
                [fromId, toId] = [props.linkTarget.id, wordJson.id]
            } else {
                [fromId, toId] = [wordJson.id, props.linkTarget.id]
            }
            const r = await addLink(graph, fromId, toId, props.linkType, data.linkRuleNames, data.linkSource, data.linkNotes)
            if (r.status !== 200) {
                const jr = await r.json()
                return {message: jr.message}
            }
        }
        else if (props.newCompound === true) {
            await createCompound(graph, props.linkTarget.id, wordJson.id, data.linkSource, data.linkNotes)
        }
        else if (props.addToCompound !== undefined) {
            await addToCompound(graph, props.addToCompound, wordJson.id)
        }
        if (props.submitted !== undefined) {
            props.submitted(wordJson)
        }
    }

    async function updateWordStatus(data) {
        if (isAddingLink || props.newCompound === true || props.addToCompound !== undefined) {
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
    }

    return <EtymographForm
        create={(data) => addWord(graph, data.language, data.text, data.gloss, data.fullGloss, data.posClasses,
            data.reconstructed, data.source, data.notes)}
        update={(data) => updateWord(graph, props.updateId, data.text, data.gloss, data.fullGloss, data.posClasses,
            data.reconstructed, data.source, data.notes)}
        {...props}
        submitted={submitted}
    >

        <table><tbody>
        {props.languageReadOnly !== true && <LanguageSelect id="language" label="Language"/>}
        <FormRow id="text" label="Text" readOnly={props.textReadOnly === true} handleBlur={updateWordStatus}>
            {isNewWord && <span className="newWord">New</span>}
            {wordDefinitions.length > 0 && <span className="wordDefinitions">{wordDefinitions.join(", ")}</span>}
        </FormRow>
        <FormRow id="posClasses" label="POS/classes"/>
        <FormRow id="gloss" label="Gloss"/>
        <FormRow id="fullGloss" label="Full gloss"/>
        {props.linkType === '>' && <RuleListSelect id="linkRuleNames" label="Link rule names"/>}
        <FormRow id="source" label="Source"/>
        <tr>
            <td>Notes:</td>
            <td><FormTextArea rows="3" cols="50" id="notes"/></td>
        </tr>
        {(props.newCompound === true || isAddingLink) && <>
            <FormRow id="linkSource" label={isAddingLink ? "Link source" : "Compound source"}/>
            <FormRow id="linkNotes" label={isAddingLink ? "Link notes" : "Compound notes"}/>
        </>}
        </tbody></table>
        {!props.hideReconstructed && <FormCheckbox id="reconstructed" label="Reconstructed"/>}
    </EtymographForm>
}
