import {addRuleLink} from "@/api";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import SourceInput from "@/components/SourceInput";
import {LinkTypes} from "@/components/LinkTypes";
import {useRouter} from "next/router";

interface RuleLinkFormProps extends EtymographFormProps<LinkRuleData>{
    fromEntityId: number
}

interface LinkRuleData {
    linkRuleName: string
    source: string
    notes: string
}

export default function RuleLinkForm(props: RuleLinkFormProps) {
    const router = useRouter()
    const graph = router.query.graph as string

    return <EtymographForm<LinkRuleData>
        create={(data) => addRuleLink(graph, props.fromEntityId, data.linkRuleName, LinkTypes.Related, data.source, data.notes)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Link to rule name" id="linkRuleName"/>
            <SourceInput label="Source" id="source"/>
            <FormRow label="Notes" id="notes"/>
        </tbody></table>
    </EtymographForm>
}
