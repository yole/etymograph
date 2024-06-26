import {addRuleLink} from "@/api";
import EtymographForm from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import {useRouter} from "next/router";

export default function RuleLinkForm(props) {
    const router = useRouter()

    return <EtymographForm
        create={(data) => addRuleLink(router.query.graph, props.fromEntityId, data.linkRuleName, '~', data.source, data.notes)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Link to rule name" id="linkRuleName"/>
            <FormRow label="Source" id="source"/>
            <FormRow label="Notes" id="notes"/>
        </tbody></table>
    </EtymographForm>
}
