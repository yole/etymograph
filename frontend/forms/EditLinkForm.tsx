import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import FormRow from "@/components/FormRow";
import RuleListSelect from "@/components/RuleListSelect";
import {updateCompound, updateLink} from "@/api";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import FormSelect from "@/components/FormSelect";

export default function EditLinkForm(props) {
    const graph = useContext(GraphContext)

    function saveLink(data) {
        return updateLink(graph, props.baseWordId, props.linkWordId, props.linkType,
            data.ruleNames, data.source, data.notes)
    }

    function saveCompound(data) {
        return updateCompound(graph, props.compoundId, data.source, data.notes, data.head)
    }

    return <EtymographForm
        create={props.compoundId === undefined ? saveLink : saveCompound}
        {...props}
    >
        <table><tbody>
            {props.compoundId === undefined &&
                <RuleListSelect id="ruleNames" label="Rule names" isMulti={true} language={props.language}/>
            }
            <FormRow id="source" label="Source"/>
            <FormRow id="notes" label="Notes"/>
            {props.compoundComponents !== undefined &&
                <FormSelect id="head" label="Head" options={props.compoundComponents} selection={props.compoundHead}/>
            }
        </tbody></table>
    </EtymographForm>
}
