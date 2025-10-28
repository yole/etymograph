import {addRuleSequence, updateRuleSequence} from "@/api";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import FormTextArea from "@/components/FormTextArea";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import LanguageSelect from "@/components/LanguageSelect";
import {RuleSequenceViewModel, UpdateSequenceParams} from "@/models";

interface RuleSequenceFormProps extends EtymographFormProps<UpdateSequenceParams, RuleSequenceViewModel> {
}

export default function RuleSequenceForm(props: RuleSequenceFormProps) {
    const graph = useContext(GraphContext)
    return <EtymographForm
        create={(data) => addRuleSequence(graph, data)}
        update={(data) => updateRuleSequence(graph, props.updateId as number, data)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Name" id="name"/>
            <LanguageSelect label="From language" id="fromLang"/>
            <LanguageSelect label="To language" id="toLang"/>
        </tbody></table>
        <h3>Rules</h3>
        <FormTextArea rows={10} cols={50} id="ruleNames"/>
    </EtymographForm>
}
