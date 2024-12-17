import {addPublication, updatePublication} from "@/api";
import FormRow from "@/components/FormRow";
import EtymographForm, {EtymographFormProps} from "@/components/EtymographForm";
import {useContext} from "react";
import {GraphContext} from "@/components/Contexts";
import {AddPublicationParameters} from "@/models";

interface PublicationFormProps extends EtymographFormProps<AddPublicationParameters>{
}

export default function PublicationForm(props: PublicationFormProps) {
    const graph = useContext(GraphContext);

    return <EtymographForm<AddPublicationParameters>
        create={(data) => addPublication(graph, data)}
        update={(data) => updatePublication(graph, props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Name" id="name"/>
            <FormRow label="Reference ID" id="refId"/>
        </tbody></table>
    </EtymographForm>
}
