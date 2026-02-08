import {useContext, useState} from "react";
import {addRule, previewRuleChanges, updateRule} from "@/api";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import LanguageSelect from "@/components/LanguageSelect";
import {GraphContext} from "@/components/Contexts";
import PosSelect from "@/components/PosSelect";
import WordLink from "@/components/WordLink";
import {RuleViewModel, UpdateRuleParameters} from "@/models";
import FormCheckbox from "@/components/FormCheckbox";

interface RuleFormProps extends EtymographFormProps<UpdateRuleParameters, RuleViewModel> {
    initialType?: string
    addToSequenceId?: number
}

export default function RuleForm(props: RuleFormProps) {
    const [ruleType, setRuleType] = useState(props.initialType !== undefined ? props.initialType : "phono")
    const [preview, setPreview] = useState([])
    const graph = useContext(GraphContext)

    async function previewRuleChangesClicked(data) {
        const r = await previewRuleChanges(graph, props.updateId as number, data.text)
        if (r.status === 200) {
            const jr = await r.json()
            if (jr.results.length === 0) {
                setPreview(["No words affected"])
            }
            else {
                setPreview(jr.results.map(r => <><WordLink word={r.word}/>{`: ${r.oldForm} -> ${r.newForm}`}<br/></>))
            }
        }
    }

    return <>
        Rule type:{' '}
            <button className={ruleType === "morpho" ? "inlineButton inlineButtonActive " : "inlineButton"} onClick={() => setRuleType("morpho")}>Morphological</button>{' | '}
            <button className={ruleType === "phono" ? "inlineButton inlineButtonActive " : "inlineButton"} onClick={() => setRuleType("phono")}>Phonological</button>
        <hr/>
        <EtymographForm
            create={(data) => {
                if (ruleType === "morpho") {
                    data["fromLang"] = data["toLang"]
                }
                if (props.addToSequenceId) {
                    data["addToSequenceId"] = props.addToSequenceId
                }
                return addRule(graph, data);
            }}
            update={(data) => {
                if (ruleType === "morpho") {
                    data["fromLang"] = data["toLang"]
                }
                return updateRule(graph, props.updateId as number, data);
            }}
            buttons={props.updateId !== undefined ? [
                {text: 'Preview', callback: (data) => previewRuleChangesClicked(data)}
            ] : []}
            {...props}>

            <table><tbody>
                <FormRow label="Name" id="name"/>
                {ruleType === "phono" && <LanguageSelect label="From language" id="fromLang"/>}
                <LanguageSelect label={ruleType === "morpho" ? "Language" : "To language"} id="toLang"/>
                {ruleType === "morpho" && <>
                    <FormRow label="Added category values" id="addedCategories"/>
                    <FormRow label="Replaced category values" id="replacedCategories"/>
                    <PosSelect label="From POS" id="fromPOS" languageProp="toLang" isMulti={true}/>
                    <PosSelect label="To POS" id="toPOS" languageProp="toLang" isMulti={false} showNone={true}/>
                </>}
                <FormRow label="Source" id="source"/>
            </tbody></table>
            <FormTextArea rows={10} cols={70} id="text" className="ruleText" inputAssist={true}/>
            <FormCheckbox id="createUnresolvedEntities" label="Create unresolved entities"/>
            <br/>
            <h3>Notes</h3>
            <FormTextArea rows={5} cols={50} id="notes"/>
        </EtymographForm>
        {preview.length > 0 && <p>{preview}</p>}
    </>
}
