import {addPublication, updatePublication} from "@/api";
import FormRow from "@/components/FormRow";
import EtymographForm from "@/components/EtymographForm";
import {useRouter} from "next/router";

export default function PublicationForm(props) {
    const router = useRouter()
    const graph = router.query.graph

    return <EtymographForm
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
