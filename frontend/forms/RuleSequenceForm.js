import {addRuleSequence, updateRuleSequence} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import {useRouter} from "next/router";

export default function RuleSequenceForm(props) {
    const router = useRouter()
    const graph = router.query.graph
    return <EtymographForm
        create={(data) => addRuleSequence(graph, data)}
        update={(data) => updateRuleSequence(graph, props.updateId, data)}
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
