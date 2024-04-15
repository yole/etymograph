import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import RuleListSelect from "@/components/RuleListSelect";
import {updateLink} from "@/api";

export default function EditLinkForm(props) {
    return <EtymographForm
        create={(data) => updateLink(props.baseWordId, props.linkWordId, props.linkType,
            data.ruleNames, data.source, data.notes)}
        {...props}
    >
        <table><tbody>
            <RuleListSelect id="ruleNames" label="Rule names"/>
            <FormRow id="source" label="Source"/>
            <FormRow id="notes" label="Notes"/>
        </tbody></table>
    </EtymographForm>
}
