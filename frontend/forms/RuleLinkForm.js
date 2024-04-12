import {addRuleLink} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";

export default function RuleLinkForm(props) {
    return <EtymographForm
        create={(data) => addRuleLink(props.fromEntityId, data.linkRuleName, '~', data.source)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Link to rule name" id="linkRuleName"/>
            <FormRow label="Source" id="source"/>
        </tbody></table>
    </EtymographForm>
}
