import {useState} from "react";
import {addRule, updateRule} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import LanguageSelect from "@/components/LanguageSelect";
import {useRouter} from "next/router";

export default function RuleForm(props) {
    const [ruleType, setRuleType] = useState(props.initialType !== undefined ? props.initialType : "phono")
    const router = useRouter()
    const graph = router.query.graph

    return <>
        Rule type:{' '}
            <button className={ruleType === "morpho" ? "inlineButton inlineButtonActive " : "inlineButton"} onClick={() => setRuleType("morpho")}>Morphological</button>{' | '}
            <button className={ruleType === "phono" ? "inlineButton inlineButtonActive " : "inlineButton"} onClick={() => setRuleType("phono")}>Phonological</button>
        <hr/>
        <EtymographForm
            create={(data) => addRule(graph, data)}
            update={(data) => updateRule(graph, props.updateId, data)}
            {...props}>

            <table><tbody>
                <FormRow label="Name" id="name"/>
                {ruleType === "phono" && <LanguageSelect label="From language" id="fromLang"/>}
                <LanguageSelect label={ruleType === "morpho" ? "Language" : "To language"} id="toLang"/>
                {ruleType === "morpho" && <>
                    <FormRow label="Added category values" id="addedCategories"/>
                    <FormRow label="Replaced category values" id="replacedCategories"/>
                    <FormRow label="From POS" id="fromPOS"/>
                    <FormRow label="To POS" id="toPOS"/>
                </>}
                <FormRow label="Source" id="source"/>
            </tbody></table>
            <FormTextArea rows="10" cols="50" id="text"/>
            <br/>
            <h3>Notes</h3>
            <FormTextArea rows="5" cols="50" id="notes"/>
        </EtymographForm>
    </>
}
