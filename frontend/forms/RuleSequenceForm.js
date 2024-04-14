import {addRuleSequence, updateRuleSequence} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";

export default function RuleSequenceForm(props) {
    return <EtymographForm
        create={(data) => addRuleSequence(data)}
        update={(data) => updateRuleSequence(props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Name" id="name"/>
            <FormRow label="From language" id="fromLang"/>
            <FormRow label="To language" id="toLang"/>
        </tbody></table>
        <h3>Rules</h3>
        <FormTextArea rows="10" cols="50" id="ruleNames"/>
    </EtymographForm>
}
