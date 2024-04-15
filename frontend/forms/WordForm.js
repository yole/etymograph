import {addLink, addToCompound, addWord, createCompound, updateWord} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import LanguageSelect from "@/components/LanguageSelect";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import FormCheckbox from "@/components/FormCheckbox";

export default function WordForm(props) {
    const isAddingLink = props.linkType !== undefined

    async function submitted(wordJson, data) {
        if (isAddingLink) {
            let fromId, toId
            if (props.reverseLink === true) {
                [fromId, toId] = [props.linkTarget.id, wordJson.id]
            } else {
                [fromId, toId] = [wordJson.id, props.linkTarget.id]
            }
            const r = await addLink(fromId, toId, props.linkType, data.linkRuleNames, data.linkSource, data.linkNotes)
            if (r.status !== 200) {
                const jr = await r.json()
                return {message: jr.message}
            }
        }
        else if (props.newCompound === true) {
            await createCompound(props.linkTarget.id, wordJson.id, data.linkSource, data.linkNotes)
        }
        else if (props.addToCompound !== undefined) {
            await addToCompound(props.addToCompound, wordJson.id)
        }
        if (props.submitted !== undefined) {
            props.submitted(wordJson)
        }
    }

    return <EtymographForm
        create={(data) => addWord(data.language, data.text, data.gloss, data.fullGloss, data.posClasses,
            data.reconstructed, data.source, data.notes)}
        update={(data) => updateWord(props.updateId, data.text, data.gloss, data.fullGloss, data.posClasses,
            data.reconstructed, data.source, data.notes)}
        {...props}
        submitted={submitted}
    >

        <table><tbody>
        {props.languageReadOnly !== true && <LanguageSelect id="language" label="Language"/>}
        <FormRow id="text" label="Text" readOnly={props.textReadOnly === true}/>
        <FormRow id="posClasses" label="POS/classes"/>
        <FormRow id="gloss" label="Gloss"/>
        <FormRow id="fullGloss" label="Full gloss"/>
        {props.linkType === '>' && <FormRow id="linkRuleNames" label="Link rule names"/>}
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
