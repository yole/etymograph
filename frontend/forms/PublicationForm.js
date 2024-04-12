import {addPublication, updatePublication} from "@/api";
import FormRow from "@/components/FormRow";
import EtymographForm from "@/components/EtymographForm";

export default function PublicationForm(props) {
    return <EtymographForm
        create={(data) => addPublication(data)}
        update={(data) => updatePublication(props.updateId, data)}
        {...props}
    >
        <table><tbody>
            <FormRow label="Name" id="name"/>
            <FormRow label="Reference ID" id="refId"/>
        </tbody></table>
    </EtymographForm>
}
