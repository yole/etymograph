import {addRuleSequence, updateRuleSequence} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import LanguageSelect from "@/components/LanguageSelect";

export default function RuleSequenceForm(props) {
    const graph = useContext(GraphContext)
    return <EtymographForm
        create={(data) => addRuleSequence(graph, data)}
        update={(data) => updateRuleSequence(graph, props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Name" id="name"/>
            <LanguageSelect label="From language" id="fromLang"/>
            <LanguageSelect label="To language" id="toLang"/>
        </tbody></table>
        <h3>Rules</h3>
        <FormTextArea rows="10" cols="50" id="ruleNames"/>
    </EtymographForm>
}
